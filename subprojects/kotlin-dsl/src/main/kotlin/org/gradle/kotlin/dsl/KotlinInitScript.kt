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

import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.DefaultKotlinScript
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForGradle
import org.gradle.kotlin.dsl.template.KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArguments
import kotlin.script.templates.ScriptTemplateDefinition


private
class KotlinInitScriptCompilationConfiguration : KotlinDslStandaloneScriptCompilationConfiguration({
    filePathPattern.put("(?:.+\\.)?init\\.gradle\\.kts")
    baseClass(KotlinInitScript::class)
    implicitReceivers(Gradle::class)
})


/**
 * Base class for Gradle Kotlin DSL standalone [Gradle] scripts, aka. init scripts.
 */
@KotlinScript(
    compilationConfiguration = KotlinInitScriptCompilationConfiguration::class
)
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
)
@ScriptTemplateAdditionalCompilerArguments(
    provider = KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider::class
)
abstract class KotlinInitScript(
    host: KotlinScriptHost<Gradle>
) : DefaultKotlinScript(defaultKotlinScriptHostForGradle(host.target)), PluginAware by host.target
