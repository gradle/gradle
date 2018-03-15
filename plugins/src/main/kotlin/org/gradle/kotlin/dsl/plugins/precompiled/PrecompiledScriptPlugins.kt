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
package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.invoke

import org.gradle.kotlin.dsl.precompile.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver
import org.gradle.kotlin.dsl.precompile.PrecompiledSettingsScript

import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


/*
 * Enables the compilation of `*.gradle.kts` scripts in regular Kotlin source-sets.
 */
open class PrecompiledScriptPlugins : Plugin<Project> {

    override fun apply(project: Project) = project.run {

        afterEvaluate {

            tasks {

                "compileKotlin"(KotlinCompile::class) {
                    kotlinOptions {
                        freeCompilerArgs += listOf(
                            "-script-templates", scriptTemplates,
                            // Propagate implicit imports and other settings
                            "-Xscript-resolver-environment=${resolverEnvironment()}"
                        )
                    }
                }
            }
        }
    }

    private
    val scriptTemplates by lazy {
        listOf(
            // treat *.settings.gradle.kts files as Settings scripts
            PrecompiledSettingsScript::class.qualifiedName!!,
            // treat *.init.gradle.kts files as Gradle scripts
            PrecompiledInitScript::class.qualifiedName!!,
            // treat *.gradle.kts files as Project scripts
            PrecompiledProjectScript::class.qualifiedName!!
        ).joinToString(separator = ",")
    }

    private
    fun Project.resolverEnvironment() =
        (PrecompiledScriptDependenciesResolver.EnvironmentProperties.kotlinDslImplicitImports
            + "=\"" + implicitImports().joinToString(separator = ":") + "\"")

    private
    fun Project.implicitImports(): List<String> =
        serviceOf<ImplicitImports>().list
}
