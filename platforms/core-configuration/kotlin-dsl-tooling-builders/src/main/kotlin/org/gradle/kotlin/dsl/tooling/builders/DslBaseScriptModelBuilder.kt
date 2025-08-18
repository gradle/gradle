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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.classpath.ClassPath
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.model.kotlin.dsl.DslBaseScriptModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.File
import java.io.Serializable


internal
object DslBaseScriptModelBuilder : BuildScopeModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.tooling.model.kotlin.dsl.DslBaseScriptModel"

    override fun create(target: BuildState): DslBaseScriptModel {
        val gradle = target.mutableModel
        val moduleRegistry = gradle.serviceOf<ModuleRegistry>()
        val implicitImports = gradle.serviceOf<ImplicitImports>()
        val kotlinScriptClassPathProvider = gradle.serviceOf<KotlinScriptClassPathProvider>()
        return StandardDslBaseScriptModel(
            scriptTemplatesClassPath = moduleRegistry.scriptTemplatesClassPath,
            implicitImports = implicitImports.list,
            kotlinDslClassPath = kotlinScriptClassPathProvider.gradleKotlinDsl.asFiles
        )
    }

    private val ModuleRegistry.scriptTemplatesClassPath: List<File>
        get() = listOf("gradle-core", "gradle-tooling-api")
            .map { getModule(it) }
            .flatMap { it.allRequiredModules }
            .fold(ClassPath.EMPTY) { classPath, module -> classPath + module.classpath }
            .asFiles
}


internal
data class StandardDslBaseScriptModel(
    private val scriptTemplatesClassPath: List<File>,
    private val implicitImports: List<String>,
    private val kotlinDslClassPath: List<File>
) : DslBaseScriptModel, Serializable {

    override fun getScriptTemplatesClassPath() = scriptTemplatesClassPath

    override fun getImplicitImports() = implicitImports

    override fun getKotlinDslClassPath() = kotlinDslClassPath
}
