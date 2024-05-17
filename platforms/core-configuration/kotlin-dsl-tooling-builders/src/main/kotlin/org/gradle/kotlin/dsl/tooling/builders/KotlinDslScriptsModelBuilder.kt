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
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.time.Time
import org.gradle.kotlin.dsl.provider.PrecompiledScriptPluginsSupport
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.tooling.model.kotlin.dsl.EditorPosition
import org.gradle.tooling.model.kotlin.dsl.EditorReport
import org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File
import java.io.Serializable


private
data class StandardKotlinDslScriptsModel(
    private val scripts: List<File>,
    private val commonModel: CommonKotlinDslScriptModel,
    private val dehydratedScriptModels: Map<File, KotlinDslScriptModel>
) : KotlinDslScriptsModel, Serializable {

    override fun getScriptModels() =
        scripts.associateWith(this::hydrateScriptModel)

    private
    fun hydrateScriptModel(script: File) =
        dehydratedScriptModels.getValue(script).let { lightModel ->
            StandardKotlinDslScriptModel(
                commonModel.classPath + lightModel.classPath,
                commonModel.sourcePath + lightModel.sourcePath,
                commonModel.implicitImports + lightModel.implicitImports,
                lightModel.editorReports,
                lightModel.exceptions
            )
        }
}


internal
data class StandardKotlinDslScriptModel(
    private val classPath: List<File>,
    private val sourcePath: List<File>,
    private val implicitImports: List<String>,
    private val editorReports: List<EditorReport>,
    private val exceptions: List<String>
) : KotlinDslScriptModel, Serializable {

    override fun getClassPath() = classPath

    override fun getSourcePath() = sourcePath

    override fun getImplicitImports() = implicitImports

    override fun getEditorReports() = editorReports

    override fun getExceptions() = exceptions
}


private
data class StandardEditorReport(
    private val severity: EditorReportSeverity,
    private val message: String,
    private val position: EditorPosition? = null
) : EditorReport, Serializable {

    override fun getSeverity() = severity

    override fun getMessage() = message

    override fun getPosition() = position
}


internal
data class StandardEditorPosition(
    private val line: Int,
    private val column: Int = 0
) : EditorPosition, Serializable {

    override fun getLine() = line

    override fun getColumn() = column
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
            return project.leaseRegistry.allowUncontrolledAccessToAnyProject {
                buildFor(parameter, project).also {
                    log("$parameter => $it - took ${timer.elapsed}")
                }
            }
        } catch (ex: Exception) {
            log("$parameter => $ex - took ${timer.elapsed}")
            throw ex
        }
    }

    private
    val Project.leaseRegistry: ProjectLeaseRegistry
        get() = serviceOf()

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
): Pair<CommonKotlinDslScriptModel, Map<File, KotlinDslScriptModel>> {

    val commonClassPath = commonPrefixOf(scriptModels.values.map { it.classPath })
    val commonSourcePath = commonPrefixOf(scriptModels.values.map { it.sourcePath })
    val commonImplicitImports = commonPrefixOf(scriptModels.values.map { it.implicitImports })

    val commonModel = CommonKotlinDslScriptModel(commonClassPath, commonSourcePath, commonImplicitImports)

    val dehydratedScriptModels = scriptModels.mapValues { (_, model) ->
        StandardKotlinDslScriptModel(
            model.classPath.drop(commonClassPath.size),
            model.sourcePath.drop(commonSourcePath.size),
            model.implicitImports.drop(commonImplicitImports.size),
            mapEditorReports(model.editorReports),
            model.exceptions
        )
    }

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

    // Init Scripts
    project
        .gradle
        .startParameter
        .allInitScripts
        .filter(File::isFile)
        .filter { it.name.endsWith(extension) }
        .forEach { yield(it) }

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

        // Precompiled Scripts
        if (p.plugins.hasPlugin("org.gradle.kotlin.kotlin-dsl")) {
            yieldAll(p.precompiledScriptPluginsSupport.collectScriptPluginFilesOf(p))
        }
    }
}.toList()


private
val Project.precompiledScriptPluginsSupport
    get() = serviceOf<PrecompiledScriptPluginsSupport>()


private
data class KotlinDslScriptsParameter(
    var correlationId: String?,
    var scriptFiles: List<File>
)


internal
fun <T : Any> commonPrefixOf(lists: List<List<T>>): List<T> =
    lists.minByOrNull { it.size }?.let { maxCommonPrefix ->
        maxCommonPrefix.indices.asSequence().takeWhile { index ->
            lists.all { list -> list[index] == maxCommonPrefix[index] }
        }.lastOrNull()?.let { maxCommonIndex ->
            maxCommonPrefix.take(maxCommonIndex + 1)
        }
    } ?: emptyList()


private
fun mapEditorReports(internalReports: List<org.gradle.kotlin.dsl.tooling.models.EditorReport>): List<EditorReport> =
    internalReports.map { internalReport ->
        StandardEditorReport(
            EditorReportSeverity.valueOf(internalReport.severity.name),
            internalReport.message,
            internalReport.position?.run {
                StandardEditorPosition(line, column)
            }
        )
    }
