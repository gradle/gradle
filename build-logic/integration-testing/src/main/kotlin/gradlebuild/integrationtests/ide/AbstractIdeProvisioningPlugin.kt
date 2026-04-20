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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*


abstract class AbstractIdeProvisioningPlugin<T : Any>(
    private val ideRuntimeConfigurationName: String,
    private val unpackTaskName: String
) : Plugin<Project> {

    protected enum class Platform {
        WINDOWS, LINUX, MAC_INTEL, MAC_ARM
    }

    protected abstract fun createExtension(extensions: ExtensionContainer): T

    protected abstract fun RepositoryHandler.ideRepositories(extension: T)

    protected abstract fun ideRuntimeDependency(extension: T, platform: Platform): Provider<String>

    protected abstract fun ExtractIdeTask.configureUnpacking(extension: T)

    override fun apply(target: Project) {
        with(target) {
            val extension = createExtension(extensions)
            repositories.ideRepositories(extension)
            val platform = when {
                BuildEnvironment.isWindows -> Platform.WINDOWS
                BuildEnvironment.isLinux -> Platform.LINUX
                BuildEnvironment.isMacOsX && BuildEnvironment.isIntel -> Platform.MAC_INTEL
                BuildEnvironment.isMacOsX && !BuildEnvironment.isIntel -> Platform.MAC_ARM
                else -> error("Unsupported platform: ${OperatingSystem.current()}")
            }
            val ideRuntime = configurations.create(ideRuntimeConfigurationName)
            dependencies {
                ideRuntime(ideRuntimeDependency(extension, platform))
            }

            tasks.register<ExtractIdeTask>(unpackTaskName) {
                ideDistribution.setFrom(ideRuntime)
                configureUnpacking(extension)
            }
        }
    }
}
