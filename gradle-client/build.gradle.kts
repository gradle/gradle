@file:Suppress("UnstableApiUsage")
import org.gradle.kotlin.dsl.desktop
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Year

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.jetbrainsCompose)
}

desktopComposeApp {
    group = "org.gradle.client"

    // Version must be strictly x.y.z and >= 1.0.0
    // for native packaging to work across platforms
    version = "1.1.3"

    kotlinApplication {
        dependencies {
            implementation(project.dependencies.platform(libs.kotlin.bom))
            implementation(project.dependencies.platform(libs.kotlinx.coroutines.bom))
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            implementation(project.dependencies.platform(libs.ktor.bom))
        }

        targets {
            jvm {
                jdkVersion = 17

                dependencies {
                    implementation(project(":build-action"))
                    implementation(project(":mutations-demo"))

                    implementation(libs.gradle.tooling.api)

                    implementation(libs.decompose.decompose)
                    implementation(libs.decompose.compose)
                    implementation(libs.essenty.lifecycle.coroutines)
                    implementation(libs.kotlinx.serialization.json)

                    implementation(libs.ktor.client.okhttp)
                    implementation(libs.ktor.serialization.kotlinx.json)

                    implementation(libs.material3WindowSizeClassMultiplatform)
                    implementation(libs.materialKolor)
                    implementation(libs.filekit.compose)

                    implementation(libs.slf4j.api)
                    implementation(libs.logback.classic)

                    implementation(libs.gradle.declarative.dsl.core)
                    implementation(libs.gradle.declarative.dsl.evaluator)
                    implementation(libs.gradle.declarative.dsl.tooling.models)

                    runtimeOnly(libs.kotlinx.coroutines.swing)

                    // TODO: Load these all into the VC
                    // Compose doesn't play well with DCL SoftwareTypes
                    // But we can determine the value of these strings at runtime and just hardcode them
                    implementation("org.jetbrains.compose.runtime:runtime:1.6.11")
                    implementation("org.jetbrains.compose.foundation:foundation:1.6.11")
                    implementation("org.jetbrains.compose.material3:material3:1.6.11")
                    implementation("org.jetbrains.compose.material:material-icons-extended:1.6.11")
                    implementation("org.jetbrains.compose.ui:ui:1.6.11")
                    implementation("org.jetbrains.compose.components:components-resources:1.6.11")
                    implementation("org.jetbrains.compose.components:components-ui-tooling-preview:1.6.11")
                    implementation("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.6.11")
                }

                testing {
                    dependencies {
                        implementation(libs.junit.junit)

                        // Compose doesn't play well with DCL SoftwareTypes
                        // But we can determine the value of this string at runtime and just hardcode it
                        implementation("org.jetbrains.compose.ui:ui-test-junit4:1.6.11")

                        runtimeOnly(libs.junit.jupiter.engine)
                    }
                }
            }
        }
    }

    sqlDelight {
        databases {
            database("ApplicationDatabase") {
                packageName = "org.gradle.client.core.database.sqldelight.generated"
                verifyDefinitions = true
                verifyMigrations = true
                deriveSchemaFromMigrations = true
                generateAsync = false
            }
        }
    }

    detekt {
        source.setFrom("src/jvmMain/kotlin", "src/jvmTest/kotlin")
        config.setFrom(rootDir.resolve("gradle/detekt/detekt.conf"))
        parallel = true
    }

    compose {
        appName = "Gradle Client"
        appDisplayName = "Gradle Client"
        appQualifiedName = "org.gradle.client"
        appUUIDFile = layout.projectDirectory.file("app-uuid.txt")

        mainClass = "org.gradle.client.GradleClientMainKt"
        jvmArgs {
            jvmArg("-Xms") {
                value = "35m"
            }
            jvmArg("-Xmx") {
                value = "128m"
            }
        }

        buildTypes {
            release {
                proguard {
                    optimize = false
                    obfuscate = false
                    configurationFiles.setFrom(layout.projectDirectory.file("proguard-desktop.pro"))
                }
            }
        }
    }
}

val appName = "GradleClient"
val appDisplayName = "Gradle Client"
val appQualifiedName = "org.gradle.client"
val appUUID = file("app-uuid.txt").readText().trim()

compose.desktop {
    application {
        mainClass = "org.gradle.client.GradleClientMainKt"
        jvmArgs += "-Xms35m"
        jvmArgs += "-Xmx128m"

        buildTypes.release.proguard {
            optimize = false
            obfuscate = false
            configurationFiles.from(layout.projectDirectory.file("proguard-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = appName
            packageVersion = "1.1.3"
// TODO: restore this            packageVersion = project.version.toString()
            description = appDisplayName
            vendor = "Gradle"
            copyright = "© ${Year.now()} the original author or authors."
            appResourcesRootDir = layout.projectDirectory.dir("src/assets")
            jvmArgs += "-splash:${'$'}APPDIR/resources/splash.png"
            modules(
                "java.instrument",
                "java.management",
                "java.naming",
                "java.scripting",
                "java.sql",
                "jdk.compiler",
                "jdk.security.auth",
                "jdk.unsupported",
            )
            linux {
                iconFile = layout.projectDirectory.file("src/assets/desktop/icon.png")
            }
            macOS {
                appStore = false
                bundleID = appQualifiedName
                dockName = appDisplayName
                iconFile = layout.projectDirectory.file("src/assets/desktop/icon.icns")
            }
            windows {
                menu = true
                menuGroup = "" // root
                perUserInstall = true
                // https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                upgradeUuid = appUUID
                iconFile = layout.projectDirectory.file("src/assets/desktop/icon.ico")
            }
        }
    }
}
