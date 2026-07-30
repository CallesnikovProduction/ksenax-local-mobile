plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "dev.openksenax"
version = "0.3.0"

android {
    namespace = "dev.openksenax.addons.contract"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "openksenax-addon-contract"
                pom {
                    name = "OpenKsenax Add-on Contract"
                    description =
                        "Stable Android IPC ABI for autonomous OpenKsenax add-on APKs."
                    licenses {
                        license {
                            name = "Apache License 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "localRegistry"
                url = uri(
                    layout.buildDirectory
                        .dir("registry-maven")
                        .get()
                        .asFile,
                )
            }
        }
    }
}

// See the app module note: Windows aidl.exe may emit an ANSI header when
// the temporary path contains Cyrillic characters.
afterEvaluate {
    tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        options.encoding = if (
            System.getProperty("os.name").startsWith("Windows")
        ) {
            "windows-1251"
        } else {
            "UTF-8"
        }
    }
}
