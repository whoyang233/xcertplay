import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val personalBuildId = providers.environmentVariable("PERSONAL_BUILD_ID")
    .orElse(providers.gradleProperty("xcertplay.buildId")).orNull
val personalVersionCode = personalBuildId?.let {
    require(it.matches(Regex("[0-9]{10}")) && it.takeLast(2).toInt() in 1..99)
    LocalDate.parse(it.take(8), DateTimeFormatter.BASIC_ISO_DATE)
    require(it.toLong() in 2000010101L..2100000000L)
    it.toInt()
} ?: 1402 // Source-only tests/lint need no allocated installable build.

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.whoyang233.xcertplay"
        minSdk = 28
        targetSdk = 37
        versionCode = personalVersionCode
        versionName = "1.4.2"

    }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.environmentVariable("ANDROID_KEYSTORE_PATH")
                    .getOrElse("missing-release-keystore.jks"),
            )
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "B"
            // Never use the per-runner Android debug key. An explicit bootstrap can be unsigned.
            signingConfig = if (providers.environmentVariable("ANDROID_KEYSTORE_PATH").isPresent) {
                signingConfigs.getByName("release")
            } else null
        }
        release {
            versionNameSuffix = "B"
            isDebuggable = false
            optimization {
                enable = false
            }
            signingConfig = if (providers.environmentVariable("ANDROID_KEYSTORE_PATH").isPresent) {
                signingConfigs.getByName("release")
            } else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.app.automotive)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Pin all packaged personal APKs to the same private key, including local builds.
tasks.matching { it.name == "packageDebug" || it.name == "packageRelease" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPersonalSigning"))
    dependsOn("verifyPersonalBuildId")
}

tasks.register("verifyPersonalBuildId") {
    val buildId = personalBuildId
    doLast {
        check(buildId != null) { "Set PERSONAL_BUILD_ID or -Pxcertplay.buildId=YYYYMMDDNN before packaging." }
    }
}
