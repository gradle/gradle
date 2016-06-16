package org.gradle.script.lang.kotlin.support

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.selectGradleApiJars
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File
import java.io.Serializable
import javax.inject.Inject

interface KotlinBuildScriptModel {
    val classPath: List<File>
}

class KotlinBuildScriptModelPlugin @Inject constructor(
    val modelBuilderRegistry: ToolingModelBuilderRegistry,
    val classPathRegistry: ClassPathRegistry) : Plugin<Project>, ToolingModelBuilder {

    override fun apply(project: Project) {
        modelBuilderRegistry.register(this)
    }

    override fun buildAll(modelName: String, project: Project): Any =
        StandardKotlinBuildScriptModel(
            (gradleApi() + scriptClassPath(project)).distinct())

    private fun gradleApi() =
        selectGradleApiJars(classPathRegistry)

    private fun scriptClassPath(project: Project) =
        (project.buildscript as ScriptHandlerInternal).scriptClassPath.asFiles

    override fun canBuild(modelName: String): Boolean =
        modelName == KotlinBuildScriptModel::class.qualifiedName
}

class StandardKotlinBuildScriptModel(override val classPath: List<File>) : KotlinBuildScriptModel, Serializable {
}
