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
package org.gradle.kotlin.dsl.plugins.embedded

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.util.internal.VersionNumber
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion


/**
 * Compares the Kotlin compiler version of a project with the Kotlin version embedded in Gradle.
 *
 * The Kotlin compile tasks depend on this task, and the comparison then happens after the
 * configuration of the project. A read of the compiler version finalizes that value, and an earlier
 * read breaks every build that configures the value.
 */
@UntrackedTask(because = "Produces only console output")
internal abstract class CheckKotlinCompilerVersion : DefaultTask() {

    companion object {
        private const val RUN_COMPILER_VIA_BUILD_TOOLS_API_PROPERTY = "kotlin.compiler.runViaBuildToolsApi"
        private val FIRST_VERSION_WITH_BUILD_TOOLS_API_BY_DEFAULT = VersionNumber.parse("2.3.20")
    }

    @get:Console
    abstract val kotlinCompilerVersion: Property<String>

    init {
        kotlinCompilerVersion.set(project.effectiveKotlinCompilerVersion())
        kotlinCompilerVersion.finalizeValueOnRead()
    }

    @TaskAction
    fun checkKotlinCompilerVersion() {
        logger.warnOnDifferentKotlinVersion(kotlinCompilerVersion.get())
    }

    /**
     * The `compilerVersion` property selects the compiler only when the compilation runs through the
     * Build Tools API. The legacy path always compiles with the compiler of the plugin version.
     *
     * The result stays a provider, because a read of `compilerVersion` finalizes that value.
     */
    @OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalBuildToolsApi::class)
    private fun Project.effectiveKotlinCompilerVersion(): Provider<String> =
        getKotlinPluginVersion().let { kotlinPluginVersion ->
            runsCompilerViaBuildToolsApi(kotlinPluginVersion)
                .zip(kotlinExtension.compilerVersion.orElse(kotlinPluginVersion)) { viaBuildToolsApi, compilerVersion ->
                    if (viaBuildToolsApi) compilerVersion else kotlinPluginVersion
                }
        }

    /**
     * The Kotlin Gradle Plugin runs the compilation through the Build Tools API since version 2.3.20.
     * Older versions need the Gradle property, and every version accepts it.
     *
     * This reads the Gradle properties only. The Kotlin Gradle Plugin also accepts the property from
     * the extra properties of the project, and from the `local.properties` file of the root project.
     */
    private fun Project.runsCompilerViaBuildToolsApi(kotlinPluginVersion: String): Provider<Boolean> =
        providers.gradleProperty(RUN_COMPILER_VIA_BUILD_TOOLS_API_PROPERTY)
            .map { it.toBoolean() }
            .orElse(VersionNumber.parse(kotlinPluginVersion).baseVersion >= FIRST_VERSION_WITH_BUILD_TOOLS_API_BY_DEFAULT)
}
