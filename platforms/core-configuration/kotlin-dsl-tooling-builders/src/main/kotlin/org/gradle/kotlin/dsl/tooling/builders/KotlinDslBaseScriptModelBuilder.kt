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

import org.gradle.internal.build.BuildState
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.model.kotlin.dsl.KotlinDslBaseScriptModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.File
import java.io.Serializable


internal
object KotlinDslBaseScriptModelBuilder : BuildScopeModelBuilder {
    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.tooling.model.kotlin.dsl.KotlinDslBaseScriptModel"

    override fun create(target: BuildState): KotlinDslBaseScriptModel {
        val implicitImports = target.getMutableModel().serviceOf<ImplicitImports>()
        val kotlinScriptClassPathProvider = target.getMutableModel().serviceOf<KotlinScriptClassPathProvider>()
        return StandardKotlinDslBaseScriptModel(
            implicitImports = implicitImports.list,
            kotlinDslClassPath = kotlinScriptClassPathProvider.gradleKotlinDsl.asFiles
        )
    }
}


internal
data class StandardKotlinDslBaseScriptModel(
    private val implicitImports: List<String>,
    private val kotlinDslClassPath: List<File>
) : KotlinDslBaseScriptModel, Serializable {

    override fun getImplicitImports() = implicitImports

    override fun getKotlinDslClassPath() = kotlinDslClassPath
}
