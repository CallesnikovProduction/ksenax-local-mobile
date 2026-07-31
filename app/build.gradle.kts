import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.reader(Charsets.UTF_8).use(::load)
    }
}

fun releaseSigningValue(
    propertyName: String,
    environmentName: String,
): String? = releaseSigningProperties
    .getProperty(propertyName)
    ?.takeIf(String::isNotBlank)
    ?: providers.environmentVariable(environmentName)
        .orNull
        ?.takeIf(String::isNotBlank)

val releaseStoreFile = releaseSigningValue(
    propertyName = "storeFile",
    environmentName = "OKX_RELEASE_STORE_FILE",
)
val releaseStorePassword = releaseSigningValue(
    propertyName = "storePassword",
    environmentName = "OKX_RELEASE_STORE_PASSWORD",
)
val releaseKeyAlias = releaseSigningValue(
    propertyName = "keyAlias",
    environmentName = "OKX_RELEASE_KEY_ALIAS",
)
val releaseKeyPassword = releaseSigningValue(
    propertyName = "keyPassword",
    environmentName = "OKX_RELEASE_KEY_PASSWORD",
)
val isReleaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.kolesnikovprod.ksetaorch"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kolesnikovprod.ksetaorch"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (isReleaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(checkNotNull(releaseStoreFile))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

tasks.configureEach {
    if (name.contains("Release", ignoreCase = true)) {
        doFirst {
            check(isReleaseSigningConfigured) {
                "Release signing is not configured. Add ignored " +
                    "keystore.properties or OKX_RELEASE_* environment variables."
            }
        }
    }
}

dependencies {
    implementation(project(":addon-contract"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    implementation("com.alphacephei:vosk-android:${libs.versions.voskAndroid.get()}@aar")
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
