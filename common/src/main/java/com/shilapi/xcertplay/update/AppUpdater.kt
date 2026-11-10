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
import kotlinx.coroutines.ensureActive
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

internal enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, INSTALL_PERMISSION, INSTALLING, NETWORK_ERROR, INVALID_APK, INSTALL_ERROR }
internal data class AppUpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val automatic: Boolean = true,
    val release: UpdateRelease? = null,
    val progress: Int? = null,
    val showPrompt: Boolean = false,
)

/** Per-Activity operations, cancelled with its lifecycle; installation always uses Android's UI. */
internal class AppUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val http: UpdateTransport = UpdateHttpClient(),
) {
    private val prefs = context.getSharedPreferences("xcertplay_updates", Context.MODE_PRIVATE)
    val installed = InstalledUpdateVersion.read(context)
    private val mutable = MutableStateFlow(AppUpdateState(automatic = prefs.getBoolean("automatic", true)))
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var automaticOperation = false
    private var verifiedFile: File? = null
    private var waitingForPermission = false

    fun setAutomatic(value: Boolean) {
        prefs.edit().putBoolean("automatic", value).apply()
        if (!value && automaticOperation) {
            operation?.cancel()
            mutable.update { it.copy(phase = UpdatePhase.IDLE, showPrompt = false) }
        }
        mutable.update { it.copy(automatic = value) }
    }

    fun check(automatic: Boolean = false) {
        if (automatic && !mutable.value.automatic) return
        if (automatic && mutable.value.phase in setOf(UpdatePhase.INSTALLING, UpdatePhase.INSTALL_PERMISSION)) return
        if (operation?.isActive == true) return
        automaticOperation = automatic
        verifiedFile = null
        mutable.update { it.copy(phase = UpdatePhase.CHECKING, showPrompt = false, progress = null) }
        operation = scope.launch {
            try {
                val release = withContext(Dispatchers.IO) { UpdateCatalog.latest(http.catalog(), installed.name, installed.code) }
                mutable.update { it.copy(phase = if (release == null) UpdatePhase.CURRENT else UpdatePhase.AVAILABLE,
                    release = release, showPrompt = release != null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(phase = UpdatePhase.NETWORK_ERROR, showPrompt = false) } }
        }
    }

    fun dismissPrompt() { mutable.update { it.copy(showPrompt = false) } }

    fun downloadAndInstall(install: (File) -> Unit) {
        if (operation?.isActive == true) return
        val release = mutable.value.release ?: return
        automaticOperation = false
        mutable.update { it.copy(phase = UpdatePhase.DOWNLOADING, showPrompt = false, progress = null) }
        operation = scope.launch {
            var file: File? = null
            var downloading = true
            try {
                val downloaded = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "updates")
                    directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
                    val coroutine = coroutineContext
                    var previous: Int? = -1
                    http.download(release, directory) { percent ->
                        coroutine.ensureActive()
                        if (previous != percent) {
                            previous = percent
                            mutable.update { it.copy(progress = percent) }
                        }
                    }
                }
                file = downloaded
                downloading = false
                withContext(Dispatchers.IO) { UpdateApkVerifier.verify(context, downloaded, release, installed) }
                verifiedFile = downloaded
                mutable.update { it.copy(phase = UpdatePhase.READY, progress = 100) }
                install(downloaded)
            } catch (cancelled: CancellationException) { file?.delete(); throw cancelled }
            catch (_: Exception) {
                file?.delete()
                verifiedFile = null
                mutable.update { it.copy(phase = if (downloading) UpdatePhase.NETWORK_ERROR else UpdatePhase.INVALID_APK) }
            }
        }
    }

    suspend fun installVerifiedFile(): Intent? = withContext(Dispatchers.IO) {
        val file = verifiedFile ?: return@withContext null
        val release = mutable.value.release ?: return@withContext null
        mutable.update { it.copy(phase = UpdatePhase.INSTALLING) }
        try {
            // Recheck after returning from permission settings or choosing another installer.
            UpdateApkVerifier.verify(context, file, release, installed)
            if (!context.packageManager.canRequestPackageInstalls()) {
                waitingForPermission = true
                mutable.update { it.copy(phase = UpdatePhase.INSTALL_PERMISSION) }
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            } else {
                waitingForPermission = false
                mutable.update { it.copy(phase = UpdatePhase.INSTALLING) }
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
    fun retryInstall(install: (File) -> Unit) {
        verifiedFile?.let(install) ?: downloadAndInstall(install)
    }
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
