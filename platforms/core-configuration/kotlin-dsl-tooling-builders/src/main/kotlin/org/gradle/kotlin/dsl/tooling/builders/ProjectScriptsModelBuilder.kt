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
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.ProjectScriptModel
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File

object ProjectScriptsModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean =
        ProjectScriptModel::class.java.name == modelName

    override fun buildAll(modelName: String, project: Project): ProjectScriptModel {

        return StandardProjectScriptModel(
            buildScriptModel = StandardGradleScriptModel(
                scriptFile = project.buildFile,
                implicitImports = emptyList(),
                contextPath = emptyList(),
            ),
            precompiledScriptModels = emptyMap()
        )
    }
}

class StandardProjectScriptModel(
    private val buildScriptModel: GradleScriptModel,
    private val precompiledScriptModels: Map<File, GradleScriptModel>
) : ProjectScriptModel {
    override fun getBuildScriptModel(): GradleScriptModel = buildScriptModel
    override fun getPrecompiledScriptModels(): Map<File, GradleScriptModel> = precompiledScriptModels
}
