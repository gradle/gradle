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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*


// Android Studio Panda 2 | 2025.3.2 March 3, 2026
// Find all references here https://developer.android.com/studio/archive
// Update verification-metadata.xml
const val DEFAULT_ANDROID_STUDIO_VERSION = "2025.3.2.6"

//TODO: this is a temporary fix to support new naming scheme of Android Studio distributions
const val NAME_FILE_PART = "panda2"
const val ANDROID_STUDIO_INSTALL_PATH = "android-studio"

private fun determineExtension(version: String): String {
    // since 2024.x Android Studio is only distributed as dmg
    return when {
        BuildEnvironment.isWindows -> "windows.zip"
        BuildEnvironment.isLinux -> "linux.tar.gz"
        BuildEnvironment.isMacOsX && BuildEnvironment.isIntel -> "mac.dmg"
        BuildEnvironment.isMacOsX && !BuildEnvironment.isIntel -> "mac_arm.dmg"
        else -> error("Unsupported version/OS: ${version}/${OperatingSystem.current()}")
    }
}

class AndroidStudioProvisioningPlugin : Plugin<Project> {
    companion object {
        const val UNPACK_TASK_NAME = "unpackAndroidStudio"
    }

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
                    url = uri("https://repo.grdev.net/artifactory/android-studio/${if (androidStudioFileName.endsWith("dmg")) "install" else "ide-zips"}")
                    patternLayout {
                        artifact("[revision]/[artifact]-$NAME_FILE_PART-[ext]")
                    }
                    metadataSources { artifact() }
                    content {
                        includeGroup("android-studio")
                    }
                }
            }

            val androidStudioRuntime = configurations.create("androidStudioRuntime")

            dependencies {
                androidStudioRuntime("android-studio:android-studio:$androidStudioVersion@$androidStudioFileName")
            }

            tasks.register(UNPACK_TASK_NAME, ExtractAndroidStudioTask::class) {
                this.androidStudioRuntime.setFrom(androidStudioRuntime)
                outputDir.set(layout.buildDirectory.dir(ANDROID_STUDIO_INSTALL_PATH))
            }
        }
    }
}


abstract class AndroidStudioProvisioningExtension {

    abstract val androidStudioVersion: Property<String>

}
