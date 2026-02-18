/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.kotlin.dsl.get
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.ProjectScriptsModel
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File

object ProjectScriptsModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean =
        ProjectScriptsModel::class.java.name == modelName

    override fun buildAll(modelName: String, project: Project): ProjectScriptsModel {
        return StandardProjectScriptsModel(
            buildScriptModel = StandardGradleScriptModel(
                scriptFile = project.buildFile,
                implicitImports = project.gradle.scriptImplicitImports,
                contextPath = buildContextPathFor(project),
            ),
            precompiledScriptModels = emptyMap()
        )
    }

    private fun buildContextPathFor(project: Project): List<ScriptContextPathElement> =
        buildList {
            // TODO incomplete and doesn't have the component identifiers
            val compilationClassPath = project.scriptCompilationClassPath.asFiles

            val resolvedClassPath: MutableSet<ResolvedArtifactResult> = hashSetOf()
            for (buildscript in sourceLookupScriptHandlersFor(project).asReversed()) {
                resolvedClassPath += classpathDependencyArtifactsOf(buildscript)
                    .filter { dep -> dep.id !in resolvedClassPath.map { it.id } }
            }

            compilationClassPath.forEach { file ->
                add(
                    StandardScriptContextPathElement(
                        file,
                        resolvedClassPath.firstOrNull { it.file == file }
                            ?.id?.componentIdentifier
                            ?.let { componentId ->
                                listOf(
                                    StandardSourceComponentIdentifier(
                                        displayName = componentId.displayName,
                                        bytes = serialize(componentId)
                                    )
                                )
                            } ?: emptyList()
                    )
                )
            }
        }
}

internal
fun classpathDependencyArtifactsOf(buildscript: ScriptHandler): ArtifactCollection =
    buildscript
        .configurations[CLASSPATH_CONFIGURATION]
        .incoming
        .artifactView { it.lenient(true) }
        .artifacts

class StandardProjectScriptsModel(
    private val buildScriptModel: GradleScriptModel,
    private val precompiledScriptModels: Map<File, GradleScriptModel>
) : ProjectScriptsModel {
    override fun getBuildScriptModel(): GradleScriptModel = buildScriptModel
    override fun getPrecompiledScriptModels(): Map<File, GradleScriptModel> = precompiledScriptModels
}
