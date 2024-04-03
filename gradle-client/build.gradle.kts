import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.time.Year

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.sqldelight)
}

val appCodeName = project.name
val appName = "GradleClient"
val appDisplayName = "Gradle Client"
val appQualifiedName = "org.gradle.client"
val appUUID = file("app-uuid.txt").readText().trim()

kotlin {
    jvm()
    sourceSets {

        all {
            dependencies {
                implementation(project.dependencies.platform(libs.kotlin.bom))
                implementation(project.dependencies.platform(libs.kotlinx.coroutines.bom))
                implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
                implementation(project.dependencies.platform(libs.ktor.bom))
            }
        }

        jvmMain.dependencies {

            implementation(libs.gradle.tooling)

            implementation(libs.sqldelight.extensions.coroutines)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.driver.sqlite)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.desktop.currentOs)

            implementation(libs.decompose.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.essenty.lifecycle.coroutines)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.material3WindowSizeClassMultiplatform)
            implementation(libs.materialKolor)

            implementation(libs.slf4j.api)
            implementation(libs.logback.classic)

            runtimeOnly(libs.kotlinx.coroutines.swing)
        }

        jvmTest.dependencies {
            implementation(libs.junit.junit)
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

sqldelight {
    databases {
        create("ApplicationDatabase") {
            packageName = "org.gradle.client.core.database.sqldelight.generated"
            verifyDefinitions = true
            verifyMigrations = true
            deriveSchemaFromMigrations = true
            generateAsync = false
        }
    }
}

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
            packageVersion = project.version.toString()
            description = appDisplayName
            vendor = "Gradle"
            copyright = "© ${Year.now()} the original author or authors."
            appResourcesRootDir = layout.projectDirectory.dir("src/assets")
            jvmArgs += "-splash:${'$'}APPDIR/resources/splash.png"
            modules(
                "java.management",
                "java.naming",
                "java.sql",
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

enum class DesktopOS(val id: String) {
    Linux("linux"),
    Mac("macos"),
    Windows("windows");
}

val currentDesktopOS: DesktopOS by lazy {
    val os = System.getProperty("os.name")
    when {
        os.startsWith("Linux", ignoreCase = true) -> DesktopOS.Linux
        os.equals("Mac OS X", ignoreCase = true) -> DesktopOS.Mac
        os.startsWith("Win", ignoreCase = true) -> DesktopOS.Windows
        else -> error("Unknown OS name: $os")
    }
}

// Package as ZIP
afterEvaluate {
    val arch = System.getProperty("os.arch")
    val distDir = layout.buildDirectory.dir("dist")
    val createDistributable by tasks.existing(AbstractJPackageTask::class)
    tasks.register<Zip>("packageZip") {
        archiveFileName = "${project.name}-${project.version}-${currentDesktopOS.id}-$arch-debug.zip"
        from(createDistributable.flatMap { it.destinationDir })
        destinationDirectory = distDir
    }
    val createReleaseDistributable by tasks.existing(AbstractJPackageTask::class)
    tasks.register<Zip>("packageReleaseZip") {
        archiveFileName = "${project.name}-${project.version}-${currentDesktopOS.id}-$arch.zip"
        from(createReleaseDistributable.flatMap { it.destinationDir })
        destinationDirectory = distDir
    }
}
