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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import java.io.File
import java.util.Properties

class IdeProvisioningPlugin : Plugin<Project> {

    companion object {
        private const val VERSIONS_FILE = "gradle/dependency-management/smoke-tested-ides.properties"

        private const val INTELLIJ_ULTIMATE_ENTRY = "intellij.ultimate"
        private const val ANDROID_STUDIO_ENTRY = "android.studio"

        fun ideArchivesProvider(project: Project): IdeArchivesProvider = project.objects.newInstance<IdeArchivesProvider>().apply {
            androidStudioArchive.from(project.configurations.named("intellijPlatformDependencyArchive_$ANDROID_STUDIO_ENTRY"))
            intellijUltimateArchive.from(project.configurations.named("intellijPlatformDependencyArchive_$INTELLIJ_ULTIMATE_ENTRY"))
        }

        private fun loadProperties(file: File): Properties {
            require(file.exists()) { "IDE versions file not found: $file" }
            return Properties().apply {
                file.inputStream().use { load(it) }
            }
        }
    }

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.intellij.platform.module")

            val versions = loadProperties(project.rootDir.resolve(VERSIONS_FILE))

            repositories.intellijPlatform {
                androidStudioInstallers()
                jetbrainsIdeInstallers()
                releases()
                jetbrainsRuntime()
                // Required for resolving bundledModule:* artifacts (e.g. intellij-platform-test-runtime) from extracted IDE archives
                localPlatformArtifacts()
            }

            // We only use the plugin for IDE provisioning, not for building IntelliJ plugins.
            extensions.getByType<IntelliJPlatformExtension>().instrumentCode.set(false)

            // The IntelliJ Platform sets `toolchain.languageVersion` convention
            extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.unsetConvention()

            // we need to declare at least one dependency on an IDE distribution, otherwise the plugin will complain
            val intellijDeps = dependencies.extensions.getByType<IntelliJPlatformDependenciesExtension>()
            intellijDeps.androidStudio(versions[ANDROID_STUDIO_ENTRY] as String)

            val testing = the<IntelliJPlatformTestingExtension>()
            versions.entries.forEach { (key, versionValue) ->
                val (platformType, installer) = when (key) {
                    ANDROID_STUDIO_ENTRY -> IntelliJPlatformType.AndroidStudio to true
                    INTELLIJ_ULTIMATE_ENTRY -> IntelliJPlatformType.IntellijIdeaUltimate to false
                    else -> error("Unknown IDE entry: $key")
                }
                testing.testIde.register(key as String) {
                    type = platformType
                    version = versionValue as String
                    useInstaller = installer
                }
            }

            // The IntelliJ Platform plugin auto-populates intellijPluginVerifierIdes with
            // the latest release for plugin verification. We don't need that — clear it.
            configurations.named("intellijPluginVerifierIdes") {
                withDependencies { clear() }
            }
        }
    }
}

abstract class IdeArchivesProvider : CommandLineArgumentProvider {
    @get: InputFiles
    @get: PathSensitive(PathSensitivity.NONE)
    abstract val androidStudioArchive: ConfigurableFileCollection

    @get: InputFiles
    @get: PathSensitive(PathSensitivity.NONE)
    abstract val intellijUltimateArchive: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf(
        "-Dandroid.studio.archive=${androidStudioArchive.singleFile.absolutePath}",
        "-Didea.ultimate.archive=${intellijUltimateArchive.singleFile.absolutePath}"
    )
}
