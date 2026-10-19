plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
}

// Browser output is an opt-in build feature, excluded from normal personal car APKs.
val webDisplayEnabled = providers.gradleProperty("xcertplay.webDisplay")
    .map { it.toBooleanStrict() }.getOrElse(false)

android {
    namespace = "com.shilapi.xcertplay.host"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main") {
            kotlin.srcDir(if (webDisplayEnabled) "src/webDisplay/java" else "src/noWebDisplay/java")
            if (webDisplayEnabled) assets.srcDir("src/webDisplay/assets")
        }
        getByName("test").kotlin.srcDir(
            if (webDisplayEnabled) "src/webDisplayTest/java" else "src/noWebDisplayTest/java",
        )
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    if (webDisplayEnabled) {
        implementation("org.nanohttpd:nanohttpd:2.3.1")
        implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    }
    api(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
