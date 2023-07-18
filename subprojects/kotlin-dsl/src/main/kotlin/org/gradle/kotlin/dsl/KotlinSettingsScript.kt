/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.plugin.use.PluginDependenciesSpec
import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArguments
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Legacy base class for Gradle Kotlin DSL standalone [Settings] scripts IDE support.
 *
 * @see KotlinSettingsScriptTemplate
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = "(?:.+\\.)?settings\\.gradle\\.kts",
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
    ],
)
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
@Deprecated("Will be removed in Gradle 9.0")
abstract class KotlinSettingsScript(
    private val host: KotlinScriptHost<Settings>
) : @Suppress("deprecation") SettingsScriptApi(host.target) /* TODO:kotlin-dsl configure implicit receiver */ {

    /**
     * The [ScriptHandler] for this script.
     */
    override fun getBuildscript(): ScriptHandler =
        host.scriptHandler

    override val fileOperations
        get() = host.fileOperations

    override val processOperations
        get() = host.processOperations

    override fun apply(action: Action<in ObjectConfigurationAction>) =
        host.applyObjectConfigurationAction(action)

    /**
     * Configures the plugin dependencies for the project's settings.
     *
     * @see [PluginDependenciesSpec]
     * @since 6.0
     */
    @Suppress("unused")
    open fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpecScope.() -> Unit): Unit =
        throw Exception(
            "The plugins {} block must not be used here. "
                + "If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = \"id\") instead."
        )
}
