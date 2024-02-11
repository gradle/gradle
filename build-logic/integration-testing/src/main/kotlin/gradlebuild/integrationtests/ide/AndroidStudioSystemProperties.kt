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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider


abstract class AndroidStudioInstallation {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val studioInstallLocation: DirectoryProperty
}


class AndroidStudioSystemProperties(
    @get:Internal
    val studioInstallation: AndroidStudioInstallation,
    @get:Internal
    val autoDownloadAndroidStudio: Boolean,
    @get:Internal
    val runAndroidStudioInHeadlessMode: Boolean,
    @get:Internal
    val androidStudioHome: Provider<String>,
    @get:Internal
    val androidStudioJvmArgs: List<String>,
    providers: ProviderFactory
) : CommandLineArgumentProvider {

    @get:Optional
    @get:Nested
    val studioInstallationProvider = providers.provider {
        if (autoDownloadAndroidStudio) {
            studioInstallation
        } else {
            null
        }
    }

    override fun asArguments(): Iterable<String> {
        val systemProperties = mutableListOf<String>()

        systemProperties.add(getStudioHome())

        if (runAndroidStudioInHeadlessMode) {
            systemProperties.add("-Dstudio.tests.headless=true")
        }
        if (androidStudioJvmArgs.isNotEmpty()) {
            systemProperties.add("-DstudioJvmArgs=${androidStudioJvmArgs.joinToString(separator = ",")}")
        }
        return systemProperties
    }

    private
    fun getStudioHome(): String {
        if (autoDownloadAndroidStudio) {
            val androidStudioPath = studioInstallation.studioInstallLocation.asFile.get().absolutePath
            return "-Dstudio.home=$androidStudioPath"
        } else if (androidStudioHome.isPresent) {
            return "-Dstudio.home=${androidStudioHome.get()}"
        }
        throw IllegalArgumentException("Android Studio home must be provided via the 'studio.home' system property, or auto downloading must be enabled via `autoDownloadAndroidStudio=true` gradle property, system property, or environment variable")
    }
}
