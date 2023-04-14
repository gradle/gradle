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

package org.gradle.kotlin.dsl.precompile

import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependencies
import org.gradle.util.internal.TextUtil.convertLineSeparatorsToUnix

import java.util.concurrent.Future

import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.PseudoFuture
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver


class PrecompiledScriptDependenciesResolver : ScriptDependenciesResolver {

    companion object {

        fun hashOf(charSequence: CharSequence) =
            hashOfNormalisedString(convertLineSeparatorsToUnix(charSequence.toString()))

        fun hashOfNormalisedString(charSequence: CharSequence) =
            Hashing.hashString(charSequence).toString()

        /**
         * **Optimisation note**: assumes [scriptText] contains only `\n` line separators as any script text
         * coming from the Kotlin compiler already should.
         */
        fun implicitImportsForScript(scriptText: CharSequence, environment: Environment?) =
            implicitImportsFrom(environment) + precompiledScriptPluginImportsFrom(environment, scriptText)

        private
        fun implicitImportsFrom(environment: Environment?) =
            environment.stringList(EnvironmentProperties.kotlinDslImplicitImports)

        private
        fun precompiledScriptPluginImportsFrom(environment: Environment?, scriptText: CharSequence): List<String> =
            environment.stringList(hashOfNormalisedString(scriptText))

        private
        fun Environment?.stringList(key: String) =
            string(key)?.split(':')
                ?: emptyList()

        private
        fun Environment?.string(key: String) =
            this?.get(key) as? String
    }

    object EnvironmentProperties {
        const val kotlinDslImplicitImports = "kotlinDslImplicitImports"
        const val projectRoot = "projectRoot"
    }

    override fun resolve(
        script: ScriptContents,
        environment: Environment?,
        report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit,
        previousDependencies: KotlinScriptExternalDependencies?
    ): Future<KotlinScriptExternalDependencies?> =

        PseudoFuture(
            KotlinBuildScriptDependencies(
                classpath = emptyList(),
                sources = emptyList(),
                imports = implicitImportsForScript(script.text!!, environment),
                javaHome = null,
                classPathBlocksHash = null
            )
        )
}
