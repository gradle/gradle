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

package org.gradle.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.builders.scriptCompilationClassPath
import org.gradle.kotlin.dsl.tooling.builders.scriptImplicitImports
import org.gradle.kotlin.dsl.tooling.builders.sourceLookupScriptHandlersFor
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
                contextPath = contextPathFor(project.buildFile, project),
            ),
            precompiledScriptModels = emptyList()
        )
    }

    private fun contextPathFor(scriptFile: File, project: Project): List<ScriptContextPathElement> =
        project.gradle.serviceOf<GradleScriptModelDependencies>().contextPathFor(
            scriptFile = scriptFile,
            classPathFiles = project.scriptCompilationClassPath.asFiles,
            scriptHandlers = sourceLookupScriptHandlersFor(project).asReversed()
        )
}
