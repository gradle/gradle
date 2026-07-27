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

import gradlebuild.basics.BuildParams
import gradlebuild.basics.androidStudioHome
import gradlebuild.basics.autoDownloadIde
import gradlebuild.basics.ideaHome
import gradlebuild.basics.runIdeInHeadlessMode
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider

/**
 * Supported IDEs for sync testing.
 *
 * @param finderHomeSysPropName system property read by the profiler's IDE finders
 *   ([AndroidStudioFinder][org.gradle.profiler.ide.tools.AndroidStudioFinder],
 *   [IntellijFinder][org.gradle.profiler.ide.tools.IntellijFinder]) to locate the IDE installation.
 * @param localHomePropName Gradle property the user sets to point to a local IDE installation.
 */
private enum class Ide(val finderHomeSysPropName: String, val localHomePropName: String) {
    AndroidStudio("studio.home", BuildParams.STUDIO_HOME),
    IntellijIdea("idea.home", BuildParams.IDEA_HOME),
}

private abstract class IdeInstallation {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val installLocation: DirectoryProperty
}

private class IdeSystemPropertiesProvider(
    @get:Internal
    val installation: IdeInstallation,
    @get:Internal
    val autoDownload: Provider<Boolean>,
    @get:Internal
    val headlessMode: Provider<Boolean>,
    @get:Internal
    val home: Provider<String>,
    @get:Internal
    val ide: Ide,
    @get:Internal
    val additionalSystemProperties: List<String>
) : CommandLineArgumentProvider {

    @get:Optional
    @get:Nested
    @Suppress("UNUSED") // required to ensure that the installation directory is treated as an input
    val installationProvider = autoDownload.map { if (it) installation else null }

    override fun asArguments(): Iterable<String> {
        val systemProperties = mutableListOf<String>()

        systemProperties.add(getIdeHome())

        if (headlessMode.get()) {
            systemProperties.add("-Dide.tests.headless=true")
        }
        systemProperties.addAll(additionalSystemProperties)
        return systemProperties
    }

    private
    fun getIdeHome(): String {
        if (autoDownload.get()) {
            val idePath = installation.installLocation.asFile.get().absolutePath
            return "-D${ide.finderHomeSysPropName}=$idePath"
        } else if (home.isPresent) {
            return "-D${ide.finderHomeSysPropName}=${home.get()}"
        }
        throw IllegalArgumentException("${ide.name} home must be provided via the '${ide.localHomePropName}' property, or auto downloading must be enabled via `autoDownloadIde=true` gradle property, system property, or environment variable")
    }
}

fun Project.androidStudioSystemProperties(androidStudioJvmArgs: List<String> = emptyList()): CommandLineArgumentProvider {
    val additionalSystemProperties = buildList {
        if (androidStudioJvmArgs.isNotEmpty()) {
            add("-DstudioJvmArgs=${androidStudioJvmArgs.joinToString(separator = ",")}")
        }
    }
    return ideSystemProperties(Ide.AndroidStudio, additionalSystemProperties)
}

fun Project.ideaSystemProperties(): CommandLineArgumentProvider =
    ideSystemProperties(Ide.IntellijIdea, emptyList())

private fun Project.ideSystemProperties(ide: Ide, additionalSystemProperties: List<String>): CommandLineArgumentProvider {
    val extension = project.extensions.getByType<IdeProvisioningExtension>()
    val (installPath, ideHome) = when (ide) {
        Ide.AndroidStudio -> extension.androidStudioInstallPath to project.androidStudioHome
        Ide.IntellijIdea -> extension.intellijIdeaInstallPath to project.ideaHome
    }
    val installation = project.objects.newInstance<IdeInstallation>().apply {
        installLocation.set(installPath)
    }
    return IdeSystemPropertiesProvider(
        installation,
        project.autoDownloadIde,
        project.runIdeInHeadlessMode,
        ideHome,
        ide,
        additionalSystemProperties,
    )
}
