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

import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.androidStudioHome
import gradlebuild.basics.autoDownloadAndroidStudio
import gradlebuild.basics.runAndroidStudioInHeadlessMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider


// Android Studio Jellyfish 2023.3.1
// Find all references here https://developer.android.com/studio/archive
// Update verification-metadata.xml
const val DEFAULT_ANDROID_STUDIO_VERSION = "2023.3.1.18"
const val UNPACK_ANDROID_STUDIO_TASK_NAME = "unpackAndroidStudio"
const val ANDROID_STUDIO_INSTALL_PATH = "android-studio"

private fun String.is2024OrLater(): Boolean {
    val majorVersion = substringBefore('.')
    return majorVersion.toInt() >= 2024
}

private fun determineExtension(version: String): String {
    // since 2024.x Android Studio is only distributed as dmg
    val macExtension = if (version.is2024OrLater()) "dmg" else "zip"
    return when {
        BuildEnvironment.isWindows -> "windows.zip"
        BuildEnvironment.isLinux -> "linux.tar.gz"
        BuildEnvironment.isMacOsX && BuildEnvironment.isIntel -> "mac.$macExtension"
        BuildEnvironment.isMacOsX && !BuildEnvironment.isIntel -> "mac_arm.$macExtension"
        else -> error("Unsupported version/OS: ${version}/${OperatingSystem.current()}")
    }
}

class AndroidStudioProvisioningPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val androidStudioProvisioningExtension = extensions
                .create("androidStudioProvisioning", AndroidStudioProvisioningExtension::class)
                .apply {
                    androidStudioVersion.convention(DEFAULT_ANDROID_STUDIO_VERSION)
                }

            val androidStudioVersion = androidStudioProvisioningExtension.androidStudioVersion.get()
            val androidStudioFileName = determineExtension(androidStudioVersion)

            repositories {
                ivy {
                    // Url of Android Studio archive
                    url = uri("https://redirector.gvt1.com/edgedl/android/studio/${if (androidStudioFileName.endsWith("dmg")) "install" else "ide-zips"}")
                    patternLayout {
                        artifact("[revision]/[artifact]-[revision]-[ext]")
                    }
                    metadataSources { artifact() }
                    content {
                        includeGroup("android-studio")
                    }
                }
            }

            val androidStudioRuntime by configurations.creating

            dependencies {
                androidStudioRuntime("android-studio:android-studio:$androidStudioVersion@$androidStudioFileName")
            }

            tasks.register(UNPACK_ANDROID_STUDIO_TASK_NAME, ExtractAndroidStudioTask::class) {
                this.androidStudioRuntime.setFrom(androidStudioRuntime)
                outputDir.set(layout.buildDirectory.dir(ANDROID_STUDIO_INSTALL_PATH))
            }
        }
    }
}


abstract class AndroidStudioProvisioningExtension {

    abstract val androidStudioVersion: Property<String>

    fun androidStudioSystemProperties(project: Project, androidStudioJvmArgs: List<String>): CommandLineArgumentProvider {
        val unpackAndroidStudio = project.tasks.named("unpackAndroidStudio", ExtractAndroidStudioTask::class.java)
        val androidStudioInstallation = project.objects.newInstance<AndroidStudioInstallation>().apply {
            studioInstallLocation.fileProvider(unpackAndroidStudio.map { it.outputDir.asFile.get() })
        }
        return AndroidStudioSystemProperties(
            androidStudioInstallation,
            project.autoDownloadAndroidStudio,
            project.runAndroidStudioInHeadlessMode,
            project.androidStudioHome,
            androidStudioJvmArgs,
            project.providers
        )
    }
}
