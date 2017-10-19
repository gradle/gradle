/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf
import org.gradle.kotlin.dsl.provider.kotlinScriptClassPathProviderOf
import org.gradle.kotlin.dsl.resolver.SourcePathProvider
import org.gradle.kotlin.dsl.resolver.kotlinBuildScriptModelTarget
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModelArguments

import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.io.File
import java.io.Serializable


internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, modelRequestProject: Project): KotlinBuildScriptModel =
        scriptModelFor(modelRequestProject, scriptModelArgumentsFor(modelRequestProject))

    private
    fun scriptModelArgumentsFor(modelRequestProject: Project) =
        KotlinBuildScriptModelArguments(modelRequestProject.findProperty(kotlinBuildScriptModelTarget) as String?)

    private
    fun scriptModelFor(modelRequestProject: Project, arguments: KotlinBuildScriptModelArguments) =
        scriptModelRequestFor(modelRequestProject, arguments).let {
            when (it) {
                is ScriptModelRequest.ForSettingsScript -> settingsScriptModelFor(it)
                is ScriptModelRequest.ForScriptPlugin   -> scriptPluginScriptModelFor(it)
                is ScriptModelRequest.ForProjectScript  -> projectScriptModelFor(it)
            }
        }

    private
    fun scriptModelRequestFor(modelRequestProject: Project, arguments: KotlinBuildScriptModelArguments): ScriptModelRequest =
        arguments.scriptFile?.let {
            if (isSettingsScript(it)) {
                return ScriptModelRequest.ForSettingsScript(settingsFor(modelRequestProject))
            }
            return projectFor(it, modelRequestProject)
                ?.let { ScriptModelRequest.ForProjectScript(it) }
                ?: ScriptModelRequest.ForScriptPlugin(modelRequestProject)
        } ?: ScriptModelRequest.ForProjectScript(modelRequestProject)

    private
    val KotlinBuildScriptModelArguments.scriptFile
        get() = scriptPath?.let { canonicalFile(it) }

    private
    fun isSettingsScript(scriptFile: File) =
        scriptFile.name == "settings.gradle.kts"

    private
    fun settingsFor(modelRequestProject: Project) =
        (modelRequestProject.gradle as GradleInternal).settings

    private
    fun settingsScriptModelFor(scriptModelRequest: ScriptModelRequest.ForSettingsScript): KotlinBuildScriptModel =
        scriptModelRequest.run {
            val scriptClassPath = settingsScriptClassPathOf(settings)
            val gradleSources = gradleSourcesFor(scriptClassPath.bin, settings)
            val classpathSources = sourcePathFor(settings)
            val implicitImports = implicitImportsOf(settings)
            return StandardKotlinBuildScriptModel(
                scriptClassPath.bin.asFiles,
                (scriptClassPath.src + gradleSources + classpathSources).asFiles,
                implicitImports)
        }

    private fun scriptPluginScriptModelFor(scriptModelRequest: ScriptModelRequest.ForScriptPlugin): KotlinBuildScriptModel =
        scriptModelRequest.run {
            scriptModelFor(modelRequestProject, scriptPluginClassPathOf(modelRequestProject))
        }

    private fun projectScriptModelFor(scriptModelRequest: ScriptModelRequest.ForProjectScript): KotlinBuildScriptModel =
        scriptModelRequest.run {
            scriptModelFor(enclosingProject, projectScriptClassPathOf(enclosingProject))
        }

    private fun scriptModelFor(project: Project, scriptClassPath: ScriptClassPath): StandardKotlinBuildScriptModel {
        val gradleSources = gradleSourcesFor(scriptClassPath.bin, project)
        val classpathSources = sourcePathFor(project)
        val implicitImports = implicitImportsOf(project)
        return StandardKotlinBuildScriptModel(
            scriptClassPath.bin.asFiles,
            (scriptClassPath.src + gradleSources + classpathSources).asFiles,
            implicitImports)
    }

    private
    sealed class ScriptModelRequest {

        data class ForProjectScript(val enclosingProject: Project) : ScriptModelRequest()

        data class ForSettingsScript(val settings: Settings) : ScriptModelRequest()

        data class ForScriptPlugin(val modelRequestProject: Project) : ScriptModelRequest()
    }

    private
    data class ScriptClassPath(val bin: ClassPath, val src: ClassPath)

    private
    fun settingsScriptClassPathOf(settings: Settings): ScriptClassPath =
        ScriptClassPath(compilationClassPathOf(settings), ClassPath.EMPTY)

    private
    fun projectScriptClassPathOf(project: Project): ScriptClassPath {
        val compilationClassPath = compilationClassPathOf(project)
        val accessorsClassPath = accessorsClassPathFor(project, compilationClassPath)
        return ScriptClassPath(
            compilationClassPath + accessorsClassPath.bin,
            accessorsClassPath.src)
    }

    private
    fun scriptPluginClassPathOf(project: Project) =
        ScriptClassPath(
            DefaultClassPath.of(buildSrcClassPathOf(project) + gradleKotlinDslOf(project)),
            ClassPath.EMPTY)

    private
    fun implicitImportsOf(settings: Settings) =
        settings.serviceOf<ImplicitImports>().list

    private
    fun implicitImportsOf(project: Project) =
        project.serviceOf<ImplicitImports>().list

    private
    fun gradleSourcesFor(classPath: ClassPath, settings: Settings) =
        SourcePathProvider.sourcePathFor(classPath, settings.rootProject.projectDir, settings.gradle.gradleHomeDir)

    private
    fun gradleSourcesFor(classPath: ClassPath, project: Project) =
        SourcePathProvider.sourcePathFor(classPath, project.rootProject.projectDir, project.gradle.gradleHomeDir)

    private
    fun targetScriptFileForModelRequestOf(project: Project) =
        project.findProperty(kotlinBuildScriptModelTarget)?.let { canonicalFile(it as String) }

    private
    fun projectFor(targetBuildFile: File, project: Project) =
        project.allprojects.find { it.buildFile == targetBuildFile }

    private
    fun canonicalFile(path: String): File = File(path).canonicalFile

    private
    fun compilationClassPathOf(settings: Settings): ClassPath =
        kotlinScriptClassPathProviderOf(settings)
            .compilationClassPathOf(classLoaderScopeOf(settings))

    private
    fun compilationClassPathOf(project: Project) =
        kotlinScriptClassPathProviderOf(project)
            .compilationClassPathOf(classLoaderScopeOf(project))

    private
    fun classLoaderScopeOf(project: Project) =
        (project as ProjectInternal).classLoaderScope

    private
    fun classLoaderScopeOf(settings: Settings) =
        (settings as SettingsInternal).classLoaderScope

    private
    fun buildSrcClassPathOf(project: Project) =
        ClasspathUtil
            .getClasspath(project.buildscript.classLoader)
            .asFiles
            .filter { it.name == "buildSrc.jar" }
}


internal
data class StandardKotlinBuildScriptModel(
    override val classPath: List<File>,
    override val sourcePath: List<File>,
    override val implicitImports: List<String>) : KotlinBuildScriptModel, Serializable
