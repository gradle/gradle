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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

// Android Studio Panda 2 | 2025.3.2 March 3, 2026
// Find all references here https://developer.android.com/studio/archive
const val ANDROID_STUDIO_VERSION = "2025.3.2.6"

class AndroidStudioProvisioningPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.intellij.platform.base")

            repositories.intellijPlatform {
                androidStudioInstallers {
                    url = uri("https://repo.grdev.net/artifactory/android-studio")
                }
            }

            val intellijDeps = dependencies.extensions.getByType<IntelliJPlatformDependenciesExtension>()
            intellijDeps.androidStudio(ANDROID_STUDIO_VERSION)

            // The IntelliJ Platform plugin auto-populates intellijPluginVerifierIdes with
            // the latest release for plugin verification. We don't need that — clear it.
            configurations.named("intellijPluginVerifierIdes") {
                withDependencies { clear() }
            }
        }
    }
}
