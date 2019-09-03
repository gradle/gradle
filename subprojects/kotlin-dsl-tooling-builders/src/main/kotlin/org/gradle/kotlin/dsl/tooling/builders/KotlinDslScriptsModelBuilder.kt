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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.internal.time.Time
import org.gradle.kotlin.dsl.resolver.kotlinBuildScriptModelCorrelationId
import org.gradle.kotlin.dsl.resolver.kotlinDslScriptsModelTargets
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.kotlin.dsl.tooling.models.KotlinDslScriptsModel
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File
import java.io.Serializable


private
data class StandardKotlinDslScriptsModel(
    private val scriptModels: Map<File, KotlinBuildScriptModel>
) : KotlinDslScriptsModel, Serializable {

    override fun getScriptModels() = scriptModels
}


internal
object KotlinDslScriptsModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == KotlinDslScriptsModel::class.qualifiedName

    override fun buildAll(modelName: String, project: Project): KotlinDslScriptsModel {
        requireRootProject(project)
        val timer = Time.startTimer()
        val parameter = project.parameterFromRequest()
        try {
            return buildFor(parameter, project).also {
                println("$parameter => $it - took ${timer.elapsed}")
            }
        } catch (ex: Exception) {
            println("$parameter => $ex - took ${timer.elapsed}")
            throw ex
        }
    }

    private
    fun requireRootProject(project: Project) =
        require(project == project.rootProject) {
            "${KotlinDslScriptsModel::class.qualifiedName} can only be requested on the root project, got '$project'"
        }

    private
    fun buildFor(parameter: KotlinDslScriptsParameter, project: Project): KotlinDslScriptsModel =
        StandardKotlinDslScriptsModel(
            parameter.scriptFiles.associateWith { scriptFile ->
                KotlinBuildScriptModelBuilder.kotlinBuildScriptModelFor(
                    project,
                    KotlinBuildScriptModelParameter(scriptFile, parameter.correlationId)
                )
            }
        )
}


private
fun Project.parameterFromRequest(): KotlinDslScriptsParameter =
    KotlinDslScriptsParameter(
        findProperty(kotlinBuildScriptModelCorrelationId) as? String,
        (findProperty(kotlinDslScriptsModelTargets) as? String)
            ?.split(":")
            ?.map(::canonicalFile)
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$kotlinDslScriptsModelTargets property must be set and non empty")
    )


private
data class KotlinDslScriptsParameter(
    var correlationId: String?,
    var scriptFiles: List<File>
)
