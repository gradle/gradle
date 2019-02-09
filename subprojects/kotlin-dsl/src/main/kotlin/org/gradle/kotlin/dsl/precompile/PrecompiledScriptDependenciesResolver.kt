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

import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependencies

import java.util.concurrent.Future

import kotlin.script.dependencies.ScriptDependenciesResolver
import kotlin.script.dependencies.PseudoFuture
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.Environment


class PrecompiledScriptDependenciesResolver : ScriptDependenciesResolver {

    object EnvironmentProperties {
        const val kotlinDslImplicitImports = "kotlinDslImplicitImports"
    }

    override fun resolve(
        script: ScriptContents,
        environment: Environment?,
        report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit,
        previousDependencies: KotlinScriptExternalDependencies?
    ): Future<KotlinScriptExternalDependencies?> =

        PseudoFuture(
            KotlinBuildScriptDependencies(
                imports = implicitImportsFrom(environment),
                classpath = emptyList(),
                sources = emptyList()
            )
        )

    private
    fun implicitImportsFrom(environment: Environment?) =
        (environment?.get(EnvironmentProperties.kotlinDslImplicitImports) as? String)?.split(':')
            ?: emptyList()
}
