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

// TODO: migrate off the legacy Kotlin scripting API, deprecated since Kotlin API version 2.4
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package org.gradle.kotlin.dsl.precompile

import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.provider.PrecompiledScriptsEnvironment
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependencies
import org.gradle.kotlin.dsl.support.KotlinScriptHashing
import java.util.concurrent.Future
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.PseudoFuture
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver

/**
 * @since 4.7
 */
@Deprecated("Will be removed in Gradle 10")
class PrecompiledScriptDependenciesResolver : ScriptDependenciesResolver {

    /**
     * @since 5.3
     */
    companion object {

        init {
            DeprecationLogger.deprecateType(PrecompiledScriptDependenciesResolver::class.java)
                .willBeRemovedInGradle10()
                .undocumented()
                .nagUser()
        }

        /**
         * @since 5.3
         */
        fun hashOf(charSequence: CharSequence) =
            KotlinScriptHashing.hashOf(charSequence)

        /**
         * @since 6.3
         */
        fun hashOfNormalisedString(charSequence: CharSequence) =
            KotlinScriptHashing.hashOfNormalisedString(charSequence)

        /**
         * Returns the implicit imports shared by all precompiled scripts.
         *
         * Since Gradle 9.10.0, it does not return the type-safe accessors imports of the script,
         * because they are found by the script file name, which is not known here.
         *
         * @since 6.0
         */
        fun implicitImportsForScript(@Suppress("UnusedParameter") scriptText: CharSequence, environment: Environment?) =
            implicitImportsFrom(environment)

        private
        fun implicitImportsFrom(environment: Environment?) =
            environment.stringList(EnvironmentProperties.kotlinDslImplicitImports)

        private
        fun Environment?.stringList(key: String) =
            string(key)?.split(':')
                ?: emptyList()

        private
        fun Environment?.string(key: String) =
            this?.get(key) as? String
    }

    /**
     * @since 4.7
     */
    object EnvironmentProperties {
        /**
         * @since 4.7
         */
        const val kotlinDslImplicitImports = "kotlinDslImplicitImports"
        /**
         * @since 8.13
         */
        const val kotlinDslPluginSpecBuildersImplicitImports = "kotlinDslPluginSpecBuildersImplicitImports"
        /**
         * @since 8.1
         */
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
                imports = PrecompiledScriptsEnvironment.implicitImportsForScript(script.text!!, script.file?.name, environment),
                javaHome = null,
                classPathBlocksHash = null
            )
        )
}
