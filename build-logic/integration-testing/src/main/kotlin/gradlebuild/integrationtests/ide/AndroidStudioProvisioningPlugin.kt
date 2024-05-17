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

package gradlebuild.integrationtests.ide/*
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

import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.androidStudioHome
import gradlebuild.basics.autoDownloadAndroidStudio
import gradlebuild.basics.runAndroidStudioInHeadlessMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider
import java.util.concurrent.Callable


// Android Studio Jellyfish 2023.3.1
private
const val defaultAndroidStudioVersion = "2023.3.1.18"


class AndroidStudioProvisioningPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val androidStudioProvisioningExtension = extensions
                .create("androidStudioProvisioning", AndroidStudioProvisioningExtension::class)
                .apply {
                    androidStudioVersion.convention(defaultAndroidStudioVersion)
                }

            repositories {
                ivy {
                    // Url of Android Studio archive
                    url = uri("https://redirector.gvt1.com/edgedl/android/studio/ide-zips")
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
                val extension = when {
                    BuildEnvironment.isWindows -> "windows.zip"
                    BuildEnvironment.isMacOsX && BuildEnvironment.isIntel -> "mac.zip"
                    BuildEnvironment.isMacOsX && !BuildEnvironment.isIntel -> "mac_arm.zip"
                    BuildEnvironment.isLinux -> "linux.tar.gz"
                    else -> throw IllegalStateException("Unsupported OS: ${OperatingSystem.current()}")
                }
                val androidStudioDependencyProvider =
                    androidStudioProvisioningExtension.androidStudioVersion.map { "android-studio:android-studio:$it@$extension" }

                androidStudioRuntime(androidStudioDependencyProvider)
            }

            tasks.register<Copy>("unpackAndroidStudio") {
                from(
                    Callable {
                        val singleFile = androidStudioRuntime.singleFile
                        when {
                            singleFile.name.endsWith(".tar.gz") -> tarTree(singleFile)
                            else -> zipTree(singleFile)
                        }
                    }
                ) {
                    eachFile {
                        // Remove top folder when unzipping, that way we get rid of Android Studio.app folder that can cause issues on Mac
                        // where MacOS would kill the Android Studio process right after start, issue: https://github.com/gradle/gradle-profiler/issues/469
                        relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                    }
                }
                into(layout.buildDirectory.dir("android-studio"))
            }
        }
    }
}


abstract class AndroidStudioProvisioningExtension {

    abstract val androidStudioVersion: Property<String>

    fun androidStudioSystemProperties(project: Project, androidStudioJvmArgs: List<String>): CommandLineArgumentProvider {
        val unpackAndroidStudio = project.tasks.named("unpackAndroidStudio", Copy::class.java)
        val androidStudioInstallation = project.objects.newInstance<AndroidStudioInstallation>().apply {
            studioInstallLocation.fileProvider(unpackAndroidStudio.map { it.destinationDir })
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
