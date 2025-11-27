/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.integrationtests.androidhomewarmup

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import java.io.File
import javax.inject.Inject
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Must not cache because it modifies global Android home")
abstract class AndroidHomeWarmupTask : DefaultTask() {

    @get:OutputDirectory
    abstract val warmupProjectsDirectory: DirectoryProperty

    @get:Input
    abstract val sdkVersions: ListProperty<SdkVersion>

    @get:Internal
    abstract val rootProjectDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    val androidHome: Provider<String> = project.providers.environmentVariable("ANDROID_HOME")

    @get:Input
    val androidSdkRoot: Provider<String> = project.providers.environmentVariable("ANDROID_SDK_ROOT")

    @TaskAction
    fun warmup() {
        if (!androidHome.isPresent && !androidSdkRoot.isPresent) {
            logger.lifecycle("Skip warmning up Android home because ANDROID_HOME/ANDROID_SDK_ROOT env vars are not set.")
            return
        }
        sdkVersions.orNull.orEmpty().forEach { version ->
            val projectName = "platform-${version.compileSdk}-buildtools-${version.buildTools.replace(".", "-")}"
            val projectDir = File(warmupProjectsDirectory.get().asFile, projectName)

            logger.lifecycle("Generating project: $projectName")
            generateProject(projectDir, version)

            logger.lifecycle("Building project: $projectName")
            buildProject(projectDir)
        }

        logger.lifecycle("Android SDK warmup completed successfully!")
    }

    private fun generateProject(projectDir: File, version: SdkVersion) {
        projectDir.mkdirs()

        File(projectDir, "build.gradle").writeText(generateBuildGradle(version))
        File(projectDir, "settings.gradle.kts").writeText(generateSettingsGradleKts(version))
        File(projectDir, "gradle.properties").writeText(generateGradleProperties())

        val manifestDir = File(projectDir, "src/main")
        manifestDir.mkdirs()
        File(manifestDir, "AndroidManifest.xml").writeText(generateAndroidManifest())
    }

    private fun generateBuildGradle(version: SdkVersion): String =
        """
            |plugins {
            |    id("com.android.application") version "${version.agpVersion}"
            |}
            |
            |repositories {
            |    google()
            |    mavenCentral()
            |}
            |
            |android {
            |    namespace = "org.gradle.android.warmup"
            |    compileSdk = ${version.compileSdk}
            |    defaultConfig {
            |        applicationId = "org.gradle.android.warmup"
            |        minSdk = ${version.minSdk}
            |        targetSdk = ${version.effectiveTargetSdk}
            |    }
            |    lint {
            |        abortOnError = false
            |        checkReleaseBuilds = false
            |    }
            |}
            |
        """.trimMargin()

    private fun generateSettingsGradleKts(version: SdkVersion): String =
        """
            |pluginManagement {
            |    repositories {
            |        google()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |}
            |
            |rootProject.name = "android-warmup-platform-${version.compileSdk}"
            |
        """.trimMargin()

    private fun generateGradleProperties(): String =
        """
            |android.useAndroidX=true
            |org.gradle.warning.mode=all
            |
        """.trimMargin()

    private fun generateAndroidManifest(): String =
        """<?xml version="1.0" encoding="utf-8"?>
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            |    package="org.gradle.android.warmup">
            |    <application
            |        android:label="Warmup">
            |    </application>
            |</manifest>
        """.trimMargin()

    private fun buildProject(projectDir: File) {
        val wrapperName = if (OperatingSystem.current().isWindows) "gradlew.bat" else "gradlew"
        val gradleExecutable = rootProjectDir.file(wrapperName).get().asFile.absolutePath

        logger.info("Building project in $projectDir using $gradleExecutable")

        val result: ExecResult = execOperations.exec {
            workingDir = projectDir
            executable = gradleExecutable
            args = listOf("build", "--no-daemon", "--quiet", "-x", "lint", "-x", "lintDebug", "-x", "lintRelease")
            isIgnoreExitValue = false
        }

        if (result.exitValue != 0) {
            throw RuntimeException("Failed to build project in $projectDir (exit code: ${result.exitValue})")
        }
    }
}

