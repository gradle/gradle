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
import org.gradle.kotlin.dsl.support.delegates.ProjectDelegate
import org.gradle.kotlin.dsl.support.internalError

import org.gradle.plugin.use.PluginDependenciesSpec

import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArguments
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Temporary workaround for Kotlin 1.2.50.
 *
 * [KT-24926](https://youtrack.jetbrains.com/issue/KT-24926#comment=27-2914219)
 */
annotation class KotlinScriptTemplate


/**
 * Base class for Kotlin build scripts.
 */
@KotlinScriptTemplate
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = ".*\\.gradle\\.kts")
@ScriptTemplateAdditionalCompilerArguments([
    "-jvm-target", "1.8",
    "-Xjsr305=strict",
    "-XXLanguage:+NewInference",
    "-XXLanguage:+SamConversionForKotlinFunctions"
])
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
abstract class KotlinBuildScript(
    private val host: KotlinScriptHost<Project>
) : ProjectDelegate() {

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
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit): Unit =
        internalError()

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpecScope.() -> Unit): Unit =
        throw Exception("The plugins {} block must not be used here. "
            + "If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = \"id\") instead.")
}
