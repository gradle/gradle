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
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.delegates.ProjectDelegate
import org.gradle.kotlin.dsl.support.internalError
import org.gradle.kotlin.dsl.support.invalidPluginsCall
import org.gradle.plugin.use.PluginDependenciesSpec
import org.jetbrains.kotlin.scripting.definitions.annotationsForSamWithReceivers
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with


/**
 * Base class for Kotlin build scripts.
 */
//@ScriptTemplateDefinition(
//    resolver = KotlinBuildScriptDependenciesResolver::class,
//    scriptFilePattern = ".+(?<!(^|\\.)(init|settings))\\.gradle\\.kts"
//)
//@ScriptTemplateAdditionalCompilerArguments(
//    [
//        "-language-version", "1.7",
//        "-api-version", "1.7",
//        "-jvm-target", "1.8",
//        "-Xjvm-default=all",
//        "-Xjsr305=strict",
//        "-XXLanguage:+DisableCompatibilityModeForNewInference"
//    ],
//    provider = KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider::class
//)
@KotlinScript(
    fileExtension = "gradle.kts",
    compilationConfiguration = GradleScriptCompilationConfiguration::class
)
@GradleDsl
abstract class KotlinBuildScript(
    private val host: KotlinScriptHost<Project>
) : ProjectDelegate() /* TODO:kotlin-dsl configure Project as implicit receiver */ {

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
        invalidPluginsCall()
}

internal
object GradleScriptCompilationConfiguration : ScriptCompilationConfiguration({
    annotationsForSamWithReceivers.putIfAny(listOf(KotlinType("org.gradle.api.HasImplicitReceiver")))
    refineConfiguration {
        println("GradleScriptCompilationConfiguration: refineConfiguration")
        beforeParsing { (s, compilationConfiguration, _) ->
            println("GradleScriptCompilationConfiguration: beforeParsing: ${s.locationId}")
            compilationConfiguration.with {
                println("GradleScriptCompilationConfiguration: beforeParsing::compilationConfiguration: ${s.locationId}")
            }.asSuccess()
        }
        beforeCompiling { (s, compilationConfiguration, _) ->
            println("GradleScriptCompilationConfiguration: beforeCompiling: ${s.locationId}")
            compilationConfiguration.with {
                println("GradleScriptCompilationConfiguration: beforeCompiling::compilationConfiguration: ${s.locationId}")
            }.asSuccess()
        }
    }
})
