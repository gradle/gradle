/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.provider

import org.gradle.kotlin.dsl.support.KotlinScriptHashing
import kotlin.script.dependencies.Environment

object PrecompiledScriptsEnvironment {

    object EnvironmentProperties {
        const val kotlinDslImplicitImports = "kotlinDslImplicitImports"
        const val kotlinDslPluginSpecBuildersImplicitImports = "kotlinDslPluginSpecBuildersImplicitImports"
    }

    /**
     * **Optimisation note**: assumes [scriptText] contains only `\n` line separators as any script text
     * coming from the Kotlin compiler already should.
     */
    internal
    fun implicitImportsForScript(scriptText: CharSequence, environment: Environment?) =
        implicitImportsFrom(environment) + precompiledScriptPluginImportsFrom(environment, scriptText)

    private
    fun implicitImportsFrom(environment: Environment?) =
        environment.stringList(EnvironmentProperties.kotlinDslImplicitImports)

    private
    fun precompiledScriptPluginImportsFrom(environment: Environment?, scriptText: CharSequence): List<String> =
        environment.stringList(KotlinScriptHashing.hashOfNormalisedString(scriptText))

    private
    fun Environment?.stringList(key: String) =
        string(key)?.split(':')
            ?: emptyList()

    private
    fun Environment?.string(key: String) =
        this?.get(key) as? String
}
