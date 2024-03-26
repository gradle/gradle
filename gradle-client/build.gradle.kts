import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
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
            packageName = "org.gradle.client"
            packageVersion = "1.0.0"
            vendor = "Gradle"
            appResourcesRootDir = layout.projectDirectory.dir("src/assets")
            jvmArgs += "-splash:${'$'}APPDIR/resources/splash.png"
            modules(
                "java.naming",
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
