/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.net.URI

class StudioProvisioningPlugin : AbstractIdeProvisioningPlugin<StudioProvisioningExtension>(
    "androidStudioRuntime",
    "unpackAndroidStudio"
) {
    override fun createExtension(extensions: ExtensionContainer): StudioProvisioningExtension =
        extensions.create("androidStudioProvisioning", StudioProvisioningExtension::class.java)

    override fun RepositoryHandler.ideRepositories(extension: StudioProvisioningExtension) {
        listOf(
            "https://redirector.gvt1.com/edgedl/android/studio/ide-zips",
            "https://redirector.gvt1.com/edgedl/android/studio/install"
        ).forEach {
            ivy {
                url = URI(it)
                patternLayout {
                    artifact("[revision]/[artifact]-[ext]")
                    artifact("[revision]/[artifact]-[revision]-[ext]")
                }
                metadataSources { artifact() }
                content { includeGroup("android-studio") }
            }
        }
    }

    override fun ideRuntimeDependency(extension: StudioProvisioningExtension, platform: Platform): Provider<String> {
        val fileExtension = when (platform) {
            Platform.WINDOWS -> "windows.zip"
            Platform.LINUX -> "linux.tar.gz"
            Platform.MAC_INTEL -> "mac.dmg"
            Platform.MAC_ARM -> "mac_arm.dmg"
        }
        return extension.codename.zip(extension.version) { codename, version ->
            val artifact = if (codename.isEmpty()) "android-studio" else "android-studio-$codename"
            "android-studio:$artifact:$version@$fileExtension"
        }
    }

    override fun ExtractIdeTask.configureUnpacking(extension: StudioProvisioningExtension) {
        dmgAppName.set("Android Studio.app")
        stripTopLevelDirectory.set(true)
        outputDir.set(extension.unpackTo)
    }
}

interface StudioProvisioningExtension {
    val version: Property<String>
    val codename: Property<String>
    val unpackTo: DirectoryProperty
}
