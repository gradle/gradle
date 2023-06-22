/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.template.KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider
import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArguments
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Legacy base class for Gradle Kotlin DSL standalone [Gradle] scripts IDE support, aka. init scripts.
 *
 * @see KotlinGradleScriptTemplate
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = "(?:.+\\.)?init\\.gradle\\.kts",
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
    ],
    provider = KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider::class
)
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@Deprecated("Will be removed in Gradle 9.0")
abstract class KotlinInitScript(
    private val host: KotlinScriptHost<Gradle>
) : @Suppress("DEPRECATION") InitScriptApi(host.target) {

    /**
     * The [ScriptHandler] for this script.
     */
    val initscript: ScriptHandler
        get() = host.scriptHandler

    /**
     * Applies zero or more plugins or scripts.
     * <p>
     * The given action is used to configure an [ObjectConfigurationAction], which “builds” the plugin application.
     * <p>
     * @param action the action to configure an [ObjectConfigurationAction] with before “executing” it
     * @see [PluginAware.apply]
     */
    override fun apply(action: Action<in ObjectConfigurationAction>) =
        host.applyObjectConfigurationAction(action)

    override val fileOperations
        get() = host.fileOperations

    override val processOperations
        get() = host.processOperations
}
