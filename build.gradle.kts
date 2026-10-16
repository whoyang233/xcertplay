import java.security.KeyStore
import java.security.MessageDigest

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Public certificate fingerprint is versioned; the private key lives in GitHub Secrets/offline backup.
tasks.register("verifyPersonalSigning") {
    group = "verification"
    description = "Reject missing or changed personal APK signing keys"
    // Capture serializable values/providers during configuration, never the Project in the action.
    val keyPathProvider = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
    val passwordProvider = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
    val aliasProvider = providers.environmentVariable("ANDROID_KEY_ALIAS")
    val unsignedProvider = providers.gradleProperty("xcertplay.allowUnsigned")
    val fingerprintFile = layout.projectDirectory.file("signing/personal-certificate.sha256")
    doLast {
        val keyPath = keyPathProvider.orNull
        if (keyPath.isNullOrBlank()) {
            check(unsignedProvider.orNull == "true") {
                "Fixed personal signing key is required. Configure PERSONAL_ANDROID_SIGNING in GitHub Secrets."
            }
            println("Explicit bootstrap: producing unsigned APKs; these are not installable releases.")
        } else {
            val password = passwordProvider.get().toCharArray()
            val alias = aliasProvider.get()
            val keystore = KeyStore.getInstance("PKCS12")
            java.io.File(keyPath).inputStream().use { keystore.load(it, password) }
            check(keystore.isKeyEntry(alias)) { "Personal signing alias must contain a private key" }
            val cert = checkNotNull(keystore.getCertificate(alias)) { "Personal signing certificate is missing" }
            val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val expected = fingerprintFile.asFile.readText().trim()
            check(actual == expected) { "Signing certificate differs from the pinned personal certificate" }
            println("Personal signing certificate verified: $actual")
        }
    }
}
