/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.internalError
import org.gradle.kotlin.dsl.support.invalidPluginsCall
import org.gradle.plugin.use.PluginDependenciesSpec
import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArguments
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Legacy base class for Gradle Kotlin DSL standalone [Project] scripts IDE support, aka. build scripts.
 *
 * @see KotlinProjectScriptTemplate
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = ".+(?<!(^|\\.)(init|settings))\\.gradle\\.kts",
)
@ScriptTemplateAdditionalCompilerArguments(
    [
        "-language-version", "1.8",
        "-api-version", "1.8",
        "-Xjvm-default=all",
        "-Xjsr305=strict",
        "-Xskip-metadata-version-check",
        "-Xskip-prerelease-check",
        "-Xallow-unstable-dependencies",
        "-XXLanguage:+DisableCompatibilityModeForNewInference",
        "-XXLanguage:-TypeEnhancementImprovementsInStrictMode",
        "-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading",
    ]
)
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
@Deprecated("Will be removed in Gradle 9.0")
abstract class KotlinBuildScript(
    private val host: KotlinScriptHost<Project>
) : @Suppress("DEPRECATION") org.gradle.kotlin.dsl.support.delegates.ProjectDelegate() {

    override val delegate: Project
        get() = host.target

    /**
     * The [ScriptHandler] for this script.
     */
    override fun getBuildscript(): ScriptHandler =
        host.scriptHandler

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    @Suppress("unused")
    open fun buildscript(block: ScriptHandlerScope.() -> Unit): Unit =
        internalError()

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpecScope.() -> Unit): Unit =
        invalidPluginsCall()
}
