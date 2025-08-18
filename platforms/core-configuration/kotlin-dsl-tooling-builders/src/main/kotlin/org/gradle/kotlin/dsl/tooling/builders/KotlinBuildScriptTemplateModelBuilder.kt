/*
 * Copyright 2017 the original author or authors.
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
@file:Suppress("DEPRECATION")

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel
import org.gradle.tooling.model.dsl.DslBaseScriptModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.File
import java.io.Serializable


@Deprecated("Will be removed in Gradle 10, use DslBaseScriptModel instead")
internal
object KotlinBuildScriptTemplateModelBuilder : BuildScopeModelBuilder {

    private
    val gradleModules = listOf("gradle-core", "gradle-tooling-api")

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel"

    override fun create(target: BuildState): KotlinBuildScriptTemplateModel =
        target.mutableModel.serviceOf<ModuleRegistry>().run {
            StandardKotlinBuildScriptTemplateModel(
                gradleModules
                    .map { getModule(it) }
                    .flatMap { it.allRequiredModules }
                    .fold(ClassPath.EMPTY) { classPath, module -> classPath + module.classpath }
                    .asFiles
            )
        }.also {
            DeprecationLogger.deprecateType(KotlinBuildScriptTemplateModel::class.java)
                .replaceWith(DslBaseScriptModel::class.java.name)
                .willBecomeAnErrorInGradle10()
                .undocumented()
                .nagUser()
        }
}


@Deprecated("Will be removed in Gradle 10, use DslBaseScriptModel instead")
internal
data class StandardKotlinBuildScriptTemplateModel(
    private val classPath: List<File>
) : KotlinBuildScriptTemplateModel, Serializable {

    override fun getClassPath() = classPath
}
