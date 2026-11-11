package com.shilapi.xcertplay.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal data class InstalledUpdateVersion(val name: String, val code: Long) {
    val buildId: String? get() = code.toString().takeIf(UpdateCatalog::validBuildId)
    companion object {
        fun read(context: Context): InstalledUpdateVersion {
            @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return InstalledUpdateVersion(info.versionName.orEmpty(), info.longVersionCode)
        }
    }
}

internal enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, INSTALL_PERMISSION, INSTALLING, NETWORK_ERROR, DOWNLOAD_ERROR, INVALID_APK, INSTALL_ERROR }
internal data class AppUpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val automatic: Boolean = true,
    val release: UpdateRelease? = null,
    val progress: Int? = null,
    val showPrompt: Boolean = false,
    val downloadId: Long? = null,
    val downloadReason: Int? = null,
)

/** One production instance per process; native transfers also survive process death. */
internal class AppUpdater(
    context: Context,
    private val scope: CoroutineScope,
    private val http: UpdateTransport = UpdateHttpClient(),
    private val downloads: UpdateDownloads = SystemUpdateDownloads(context),
    private val startupGate: StartupUpdateGate = ProcessUpdateStartup.gate,
    private val verifyApk: (Context, File, UpdateRelease, InstalledUpdateVersion) -> Unit = UpdateApkVerifier::verify,
    private val pollMillis: Long = 1000,
) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("xcertplay_updates", Context.MODE_PRIVATE)
    val installed = InstalledUpdateVersion.read(context)
    private val mutable = MutableStateFlow(AppUpdateState(automatic = runCatching { prefs.getBoolean("automatic", true) }.getOrDefault(true)))
    val state = mutable.asStateFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var automaticOperation = false
    private var verifiedFile: File? = null
    private var waitingForPermission = false
    private var visible = false
    private var visibilityEpoch = 0L

    init {
        val pending = runCatching {
            val id = prefs.getLong("download_id", -1L)
            val size = prefs.getLong("download_size", -1L).takeIf { it > 0 }
            val release = UpdateCatalog.release(prefs.getString("download_file", "").orEmpty(), size)
            if (id >= 0 && release != null && release.newerThan(installed.name, installed.code)) id to release else null
        }.getOrNull()
        if (pending != null) mutable.update { it.copy(phase = UpdatePhase.DOWNLOADING, release = pending.second, downloadId = pending.first) }
        else clearDownloadRecord()
    }

    fun onHostResumed() {
        visible = true
        val state = mutable.value
        if (state.downloadId != null && state.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.DOWNLOAD_ERROR) &&
            prefs.getLong("download_id", -1L) == state.downloadId && downloadJob?.isActive != true) {
            observeDownload(state.downloadId, checkNotNull(state.release))
        }
    }

    fun onHostPaused() {
        visible = false
        visibilityEpoch++
        dismissPrompt()
        // Never cancel Android's transfer or ask to install on returning to CarPlay.
    }

    fun setAutomatic(value: Boolean) {
        prefs.edit().putBoolean("automatic", value).apply()
        if (!value && automaticOperation) {
            checkJob?.cancel()
            mutable.update { it.copy(phase = UpdatePhase.IDLE, showPrompt = false) }
        }
        mutable.update { it.copy(automatic = value) }
    }

    fun check(automatic: Boolean = false) {
        // Claim even when disabled/busy: turning it on later does not make a resume a new launch.
        if (automatic && (!startupGate.claim() || !mutable.value.automatic)) return
        if (mutable.value.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING, UpdatePhase.INSTALL_PERMISSION)) return
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        automaticOperation = automatic
        val epoch = visibilityEpoch
        verifiedFile = null
        clearDownloadRecord()
        mutable.update { it.copy(phase = UpdatePhase.CHECKING, showPrompt = false, progress = null, downloadId = null, downloadReason = null) }
        checkJob = scope.launch {
            try {
                val release = withContext(Dispatchers.IO) { UpdateCatalog.latest(http.catalog(), installed.name, installed.code) }
                mutable.update { it.copy(phase = if (release == null) UpdatePhase.CURRENT else UpdatePhase.AVAILABLE,
                    release = release, showPrompt = release != null && visible && visibilityEpoch == epoch) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(phase = UpdatePhase.NETWORK_ERROR, showPrompt = false) } }
        }
    }

    fun dismissPrompt() { mutable.update { it.copy(showPrompt = false) } }

    fun startSystemDownload() {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        val release = mutable.value.release ?: return
        automaticOperation = false
        verifiedFile = null
        mutable.update { it.copy(phase = UpdatePhase.DOWNLOADING, showPrompt = false, progress = null, downloadReason = null) }
        downloadJob = scope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    mutable.value.downloadId?.let { runCatching { downloads.remove(it) } }
                    val id = downloads.enqueue(release)
                    check(prefs.edit().putLong("download_id", id).putString("download_file", release.fileName)
                        .putLong("download_size", release.size ?: -1).commit())
                    id
                }
                mutable.update { it.copy(downloadId = id) }
                monitorDownload(id, release)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                clearDownloadRecord()
                mutable.update { it.copy(phase = UpdatePhase.DOWNLOAD_ERROR, showPrompt = false) }
            }
        }
    }

    private fun observeDownload(id: Long, release: UpdateRelease) {
        downloadJob = scope.launch { monitorDownload(id, release) }
    }

    private suspend fun monitorDownload(id: Long, release: UpdateRelease) {
        try {
            while (kotlin.coroutines.coroutineContext.isActive) {
                val status = withContext(Dispatchers.IO) { downloads.status(id) }
                when (status.phase) {
                    SystemDownloadPhase.COMPLETE -> {
                        var file: File? = null
                        try {
                            val ready = withContext(Dispatchers.IO) {
                                downloads.copyCompleted(id, release, File(context.cacheDir, "updates")).also {
                                    file = it
                                    verifyApk(context, it, release, installed)
                                }
                            }
                            verifiedFile = ready
                            mutable.update { it.copy(phase = UpdatePhase.READY, progress = 100, showPrompt = false) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            file?.delete()
                            withContext(Dispatchers.IO) { runCatching { downloads.remove(id) } }
                            clearDownloadRecord()
                            mutable.update { it.copy(phase = UpdatePhase.INVALID_APK, showPrompt = false) }
                        }
                        return
                    }
                    SystemDownloadPhase.FAILED, SystemDownloadPhase.MISSING -> {
                        clearDownloadRecord()
                        mutable.update { it.copy(phase = UpdatePhase.DOWNLOAD_ERROR, downloadReason = status.reason, showPrompt = false) }
                        return
                    }
                    else -> mutable.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = status.progress) }
                }
                delay(pollMillis)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Preserve the native ID so a later resume/process can reconnect to the transfer.
            mutable.update { it.copy(phase = UpdatePhase.DOWNLOAD_ERROR, showPrompt = false) }
        }
    }

    private fun clearDownloadRecord() {
        prefs.edit().remove("download_id").remove("download_file").remove("download_size").apply()
    }

    suspend fun installVerifiedFile(): Intent? = withContext(Dispatchers.IO) {
        val file = verifiedFile ?: return@withContext null
        val release = mutable.value.release ?: return@withContext null
        mutable.update { it.copy(phase = UpdatePhase.INSTALLING) }
        try {
            verifyApk(context, file, release, installed)
            if (!context.packageManager.canRequestPackageInstalls()) {
                waitingForPermission = true
                mutable.update { it.copy(phase = UpdatePhase.INSTALL_PERMISSION) }
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            } else {
                waitingForPermission = false
                Intent(Intent.ACTION_VIEW).setDataAndType(
                    FileProvider.getUriForFile(context, context.packageName + ".updates", file),
                    "application/vnd.android.package-archive",
                ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            mutable.update { it.copy(phase = UpdatePhase.INVALID_APK) }
            null
        }
    }

    fun installerFailed() { mutable.update { it.copy(phase = UpdatePhase.INSTALL_ERROR) } }
    fun installerReturned(install: (File) -> Unit) {
        if (waitingForPermission) {
            waitingForPermission = false
            if (context.packageManager.canRequestPackageInstalls()) verifiedFile?.let(install)
        } else if (mutable.value.phase == UpdatePhase.INSTALLING) mutable.update { it.copy(phase = UpdatePhase.READY) }
    }
    fun retryInstall(install: (File) -> Unit) { verifiedFile?.let(install) }
}

internal object ProcessAppUpdater {
    @Volatile private var instance: AppUpdater? = null
    @Synchronized fun get(context: Context): AppUpdater = instance ?: AppUpdater(context.applicationContext,
        CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)).also { instance = it }
}

internal object UpdateApkVerifier {
    const val PINNED_CERTIFICATE = "0692c68e3ca7d9ba413a4ee649bfac90d4fe63cda2e5498e11775d9f11fbe3c0"
    fun verify(context: Context, file: File, release: UpdateRelease, installed: InstalledUpdateVersion) {
        require(file.isFile && file.length() in 1..UpdateCatalog.MAX_APK_BYTES)
        require(file.canonicalFile.parentFile == File(context.cacheDir, "updates").canonicalFile)
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        @Suppress("DEPRECATION") val archive = context.packageManager.getPackageArchiveInfo(file.path, flags)
            ?: error("Invalid APK")
        @Suppress("DEPRECATION") val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val signer = { signatures: Array<android.content.pm.Signature>? -> signatures?.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b.toInt() and 255) }
        }?.toSet().orEmpty() }
        validate(archive.packageName, archive.versionName.orEmpty(), archive.longVersionCode,
            archive.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            signer(archive.signingInfo?.apkContentsSigners), signer(current.signingInfo?.apkContentsSigners),
            context.packageName, release, installed)
        require((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= android.os.Build.VERSION.SDK_INT)
    }

    fun validate(packageName: String, version: String, code: Long, debuggable: Boolean,
        signers: Set<String>, installedSigners: Set<String>, installedPackage: String,
        release: UpdateRelease, installed: InstalledUpdateVersion) {
        require(packageName == installedPackage && packageName == "com.whoyang233.xcertplay")
        require(version == release.versionName && code == release.buildId && code > installed.code)
        require(release.newerThan(installed.name, installed.code))
        require(!debuggable && signers == setOf(PINNED_CERTIFICATE) && signers == installedSigners)
    }
}
