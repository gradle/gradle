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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.time.Time
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.kotlin.dsl.tooling.models.KotlinDslModelsParameters
import org.gradle.kotlin.dsl.tooling.models.KotlinDslScriptsModel
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File
import java.io.Serializable


private
data class StandardKotlinDslScriptsModel(
    private val scripts: List<File>,
    private val commonModel: CommonKotlinDslScriptModel,
    private val dehydratedScriptModels: Map<File, KotlinBuildScriptModel>
) : KotlinDslScriptsModel, Serializable {

    override fun getScriptModels() =
        scripts.associateWith(this::hydrateScriptModel)

    private
    fun hydrateScriptModel(script: File) =
        dehydratedScriptModels.getValue(script).let { lightModel ->
            StandardKotlinBuildScriptModel(
                commonModel.classPath + lightModel.classPath,
                commonModel.sourcePath + lightModel.sourcePath,
                commonModel.implicitImports + lightModel.implicitImports,
                lightModel.editorReports,
                lightModel.exceptions,
                lightModel.enclosingScriptProjectDir
            )
        }
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
    fun buildFor(parameter: KotlinDslScriptsParameter, project: Project): KotlinDslScriptsModel {
        val scriptModels = parameter.scriptFiles.associateWith { scriptFile ->
            KotlinBuildScriptModelBuilder.kotlinBuildScriptModelFor(
                project,
                KotlinBuildScriptModelParameter(scriptFile, parameter.correlationId)
            )
        }
        val (commonModel, dehydratedScriptModels) = dehydrateScriptModels(scriptModels)
        return StandardKotlinDslScriptsModel(parameter.scriptFiles, commonModel, dehydratedScriptModels)
    }
}


private
data class CommonKotlinDslScriptModel(
    val classPath: List<File>,
    val sourcePath: List<File>,
    val implicitImports: List<String>
) : Serializable


private
fun dehydrateScriptModels(
    scriptModels: Map<File, KotlinBuildScriptModel>
): Pair<CommonKotlinDslScriptModel, Map<File, KotlinBuildScriptModel>> {

    val commonClassPath = mutableSetOf<File>()
    val commonSourcePath = mutableSetOf<File>()
    val commonImplicitImports = mutableSetOf<String>()
    var first = true
    scriptModels.values.forEach { model ->
        if (first) {
            commonClassPath.addAll(model.classPath)
            commonSourcePath.addAll(model.sourcePath)
            commonImplicitImports.addAll(model.implicitImports)
            first = false
        } else {
            commonClassPath.retainAll(model.classPath)
            commonSourcePath.retainAll(model.sourcePath)
            commonImplicitImports.retainAll(model.implicitImports)
        }
    }
    val dehydratedScriptModels = scriptModels.mapValues { (_, model) ->
        StandardKotlinBuildScriptModel(
            model.classPath.minus(commonClassPath),
            model.sourcePath.minus(commonSourcePath),
            model.implicitImports.minus(commonImplicitImports),
            model.editorReports,
            model.exceptions,
            model.enclosingScriptProjectDir
        )
    }
    val commonModel = CommonKotlinDslScriptModel(
        commonClassPath.toList(),
        commonSourcePath.toList(),
        commonImplicitImports.toList()
    )
    return commonModel to dehydratedScriptModels
}


private
fun Project.parameterFromRequest(): KotlinDslScriptsParameter =
    KotlinDslScriptsParameter(
        findProperty(KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME) as? String,
        (findProperty(KotlinDslScriptsModel.SCRIPTS_GRADLE_PROPERTY_NAME) as? String)
            ?.split("|")
            ?.asSequence()
            ?.filter { it.isNotBlank() }
            ?.map(::canonicalFile)
            ?.filter { it.isFile }
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: collectKotlinDslScripts()
    )


// TODO:kotlin-dsl naive implementation for now, refine
private
fun Project.collectKotlinDslScripts(): List<File> = sequence<File> {

    val extension = ".gradle.kts"

    // Settings Script
    val settingsScriptFile = File((project as ProjectInternal).gradle.settings.settingsScript.fileName)
    if (settingsScriptFile.isFile && settingsScriptFile.name.endsWith(extension)) {
        yield(settingsScriptFile)
    }

    allprojects.forEach { p ->

        // Project Scripts
        if (p.buildFile.isFile && p.buildFile.name.endsWith(extension)) {
            yield(p.buildFile)
        }

    }
}.toList()


private
data class KotlinDslScriptsParameter(
    var correlationId: String?,
    var scriptFiles: List<File>
)
