/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver.EnvironmentProperties.kotlinDslImplicitImports
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.listFilesOrdered

import javax.inject.Inject


abstract class ConfigurePrecompiledScriptDependenciesResolver @Inject constructor(

    private
    val implicitImports: ImplicitImports

) : DefaultTask(), SharedAccessorsPackageAware {

    @get:Internal
    abstract val metadataDir: DirectoryProperty

    private
    lateinit var onConfigure: (String) -> Unit

    fun onConfigure(action: (String) -> Unit) {
        onConfigure = action
    }

    @TaskAction
    fun configureImports() {
        val precompiledScriptPluginImports = precompiledScriptPluginImports()

        val resolverEnvironment = resolverEnvironmentStringFor(
            listOf(
                kotlinDslImplicitImports to implicitImportsForPrecompiledScriptPlugins(implicitImports)
            ) + precompiledScriptPluginImports
        )

        onConfigure(resolverEnvironment)
    }

    private
    fun precompiledScriptPluginImports(): List<Pair<String, List<String>>> =
        metadataDirFile().run {
            require(isDirectory)
            listFilesOrdered().map {
                it.name to it.readLines()
            }
        }

    private
    fun metadataDirFile() = metadataDir.get().asFile

    private
    fun resolverEnvironmentStringFor(properties: Iterable<Pair<String, List<String>>>): String =
        properties.joinToString(separator = ",") { (key, values) ->
            "$key=\"${values.joinToString(":")}\""
        }
}
