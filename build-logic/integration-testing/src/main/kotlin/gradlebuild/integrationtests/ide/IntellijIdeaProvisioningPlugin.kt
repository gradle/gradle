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

import gradlebuild.basics.BuildEnvironment
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.net.URI

class IntellijIdeaProvisioningPlugin : AbstractIdeProvisioningPlugin<IntellijIdeaProvisioningExtension>(
    "intellijIdeaRuntime",
    "unpackIntellijIdea"
) {
    override fun createExtension(extensions: ExtensionContainer): IntellijIdeaProvisioningExtension =
        extensions.create("intellijIdeaProvisioning", IntellijIdeaProvisioningExtension::class.java)

    override fun RepositoryHandler.ideRepositories(
        extension: IntellijIdeaProvisioningExtension
    ) {
        ivy {
            url = URI("https://download.jetbrains.com/idea/")
            patternLayout {
                artifact("[artifact]-[revision].[ext]")
                // ARM Mac uses dash separator: ideaIC-2025.2-aarch64.dmg
                artifact("[artifact]-[revision]-[ext]")
            }
            metadataSources { artifact() }
            content { includeGroup("intellij-idea") }
        }
    }

    override fun ideRuntimeDependency(
        extension: IntellijIdeaProvisioningExtension,
        platform: Platform
    ): Provider<String> {
        val fileExtension = when (platform) {
            Platform.WINDOWS -> "win.zip"
            Platform.LINUX -> "tar.gz"
            Platform.MAC_INTEL -> "dmg"
            Platform.MAC_ARM -> "aarch64.dmg"
        }
        return extension.version.map { version ->
            "intellij-idea:idea:$version@$fileExtension"
        }
    }

    override fun ExtractIdeTask.configureUnpacking(extension: IntellijIdeaProvisioningExtension) {
        dmgAppName.set("IntelliJ IDEA.app")
        stripTopLevelDirectory.set(!BuildEnvironment.isWindows)
        outputDir.set(extension.unpackTo)
    }
}

interface IntellijIdeaProvisioningExtension {
    val version: Property<String>
    val unpackTo: DirectoryProperty
}
