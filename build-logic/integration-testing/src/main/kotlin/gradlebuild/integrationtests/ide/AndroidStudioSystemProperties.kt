/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.integrationtests.ide

import gradlebuild.basics.androidStudioHome
import gradlebuild.basics.autoDownloadAndroidStudio
import gradlebuild.basics.runAndroidStudioInHeadlessMode
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension


private abstract class AndroidStudioInstallation {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val studioInstallLocation: DirectoryProperty
}


/**
 * Converts Android Studio build configuration into JVM system properties
 * for the performance test process.
 *
 * Instances are created by [composeAndroidStudioSystemProperties],
 * which resolves all inputs from project properties.
 */
private class AndroidStudioSystemProperties(
    @get:Internal
    val studioInstallation: AndroidStudioInstallation,
    @get:Internal
    val autoDownloadAndroidStudio: Provider<Boolean>,
    @get:Internal
    val runAndroidStudioInHeadlessMode: Provider<Boolean>,
    @get:Internal
    val androidStudioHome: Provider<String>,
    @get:Internal
    val androidStudioJvmArgs: List<String>,
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        val systemProperties = mutableListOf<String>()

        systemProperties.add(getStudioHome())

        if (runAndroidStudioInHeadlessMode.get()) {
            systemProperties.add("-Dide.tests.headless=true")
        }
        if (androidStudioJvmArgs.isNotEmpty()) {
            systemProperties.add("-DstudioJvmArgs=${androidStudioJvmArgs.joinToString(separator = ",")}")
        }
        return systemProperties
    }

    private
    fun getStudioHome(): String {
        if (autoDownloadAndroidStudio.get()) {
            val androidStudioPath = studioInstallation.studioInstallLocation.asFile.get().absolutePath
            return "-Dstudio.home=$androidStudioPath"
        } else if (androidStudioHome.isPresent) {
            return "-Dstudio.home=${androidStudioHome.get()}"
        }
        throw IllegalArgumentException("Android Studio home must be provided via the 'studioHome' system property, or auto downloading must be enabled via `autoDownloadAndroidStudio=true` gradle property, system property, or environment variable")
    }
}

/**
 * Creates an [AndroidStudioSystemProperties] argument provider from project properties.
 *
 * Relevant properties (can be set as Gradle properties, system properties, or env variables):
 * - `autoDownloadAndroidStudio` — download Studio automatically instead of requiring a local install.
 * - `studioHome` — explicit path to an Android Studio installation.
 * - `runAndroidStudioInHeadlessMode` — run Studio without a visible UI.
 *
 * @param androidStudioJvmArgs additional JVM arguments forwarded to the Studio process, such as `-Xmx8g`.
 */
fun Project.composeAndroidStudioSystemProperties(androidStudioJvmArgs: List<String>): CommandLineArgumentProvider {
    val intellijPlatformExtension = the<IntelliJPlatformExtension>()
    val androidStudioInstallation = project.objects.newInstance<AndroidStudioInstallation>().apply {
        studioInstallLocation.fileProvider(provider { intellijPlatformExtension.platformPath.toFile() })
    }
    return AndroidStudioSystemProperties(
        androidStudioInstallation,
        project.autoDownloadAndroidStudio,
        project.runAndroidStudioInHeadlessMode,
        project.androidStudioHome,
        androidStudioJvmArgs,
    )
}
