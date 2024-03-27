import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvm()
    sourceSets {

        all {
            dependencies {
                implementation(project.dependencies.platform(libs.kotlin.bom))
                implementation(project.dependencies.platform(libs.kotlinx.coroutines.bom))
                implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            }
        }

        jvmMain.dependencies {

            implementation(projects.gradleClientLogic)

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

            implementation(libs.materialKolor)

            implementation(libs.slf4j.api)
            implementation(libs.logback.classic)

            runtimeOnly(libs.kotlinx.coroutines.swing)
        }

        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
        }
    }
}


compose.desktop {
    application {
        mainClass = "org.gradle.client.ui.GradleClientUiMainKt"
        jvmArgs += "-Xms35m"
        jvmArgs += "-Xmx64m"

        buildTypes.release.proguard {
            optimize = false
            obfuscate = false
            configurationFiles.from(layout.projectDirectory.file("proguard-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = project.name
            packageVersion = project.version.toString()
            vendor = "Gradle"
            appResourcesRootDir = layout.projectDirectory.dir("src/assets")
            jvmArgs += "-splash:${'$'}APPDIR/resources/splash.png"
            modules(
                "java.naming",
                "java.sql",
            )
            macOS {
                dockName = "Gradle Client"
                appStore = false
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
