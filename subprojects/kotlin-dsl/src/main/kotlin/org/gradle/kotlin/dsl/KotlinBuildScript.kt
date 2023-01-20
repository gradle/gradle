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
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.DefaultKotlinScript
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForProject
import org.gradle.kotlin.dsl.support.internalError
import org.gradle.kotlin.dsl.support.invalidPluginsCall
import org.gradle.kotlin.dsl.template.KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArguments
import kotlin.script.templates.ScriptTemplateDefinition


private
class KotlinBuildScriptCompilationConfiguration : KotlinDslStandaloneScriptCompilationConfiguration({
    filePathPattern.put(".+(?<!(^|\\.)(init|settings))\\.gradle\\.kts")
    baseClass(KotlinBuildScript::class)
    implicitReceivers(Project::class)
})


/**
 * Base class for Gradle Kotlin DSL standalone [Project] scripts, aka. build scripts.
 */
@KotlinScript(
    compilationConfiguration = KotlinBuildScriptCompilationConfiguration::class
)
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
)
@ScriptTemplateAdditionalCompilerArguments(
    provider = KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider::class
)
@GradleDsl
abstract class KotlinBuildScript(
    host: KotlinScriptHost<Project>
) : DefaultKotlinScript(defaultKotlinScriptHostForProject(host.target)) {

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
