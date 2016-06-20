package org.gradle.script.lang.kotlin.support

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.initialization.ScriptHandlerInternal

import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.selectGradleApiJars

import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import org.gradle.api.Plugin
import org.gradle.api.Project

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
        if (isIdeaActive()) {
            saveIdeaDaemonPropertiesOf(project)
        }
        modelBuilderRegistry.register(this)
    }

    override fun canBuild(modelName: String): Boolean =
        modelName == KotlinBuildScriptModel::class.qualifiedName

    override fun buildAll(modelName: String, project: Project): Any =
        StandardKotlinBuildScriptModel(
            (gradleApi() + scriptClassPathOf(project)).distinct())

    private fun gradleApi() =
        selectGradleApiJars(classPathRegistry)

    private fun scriptClassPathOf(project: Project) =
        (project.buildscript as ScriptHandlerInternal).scriptClassPath.asFiles

    private fun isIdeaActive() =
        System.getProperty("idea.active", "false") == "true"

    private fun saveIdeaDaemonPropertiesOf(project: Project) {
        daemonPropertiesFileOf(project.projectDir).apply { parentFile.mkdirs() }.bufferedWriter().use {
            System.getProperties().store(it, null)
        }
    }
}

class StandardKotlinBuildScriptModel(override val classPath: List<File>) : KotlinBuildScriptModel, Serializable

fun daemonPropertiesFileOf(projectDir: File) = File(projectDir, ".gradle/gradle-script-kotlin-idea.properties")
