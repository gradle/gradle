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

import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.io.File
import java.io.Serializable


internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, project: Project): KotlinBuildScriptModel {

        val (targetProject, scriptClassPath) =
            scriptModelRequestFor(project).let {
                when (it) {
                    is ScriptModelRequest.ForProjectScript ->
                        it.enclosingProject to projectScriptClassPathOf(it.enclosingProject)
                    is ScriptModelRequest.ForScriptPlugin  ->
                        project to scriptPluginClassPathOf(project)
                }
            }

        val gradleSources = gradleSourcesFor(scriptClassPath.bin, targetProject)
        val kotlinLibSources = kotlinLibSourcesFor(targetProject)
        val implicitImports = implicitImportsOf(targetProject)

        return StandardKotlinBuildScriptModel(
            scriptClassPath.bin.asFiles,
            (scriptClassPath.src + gradleSources + kotlinLibSources).asFiles,
            implicitImports)
    }

    private
    sealed class ScriptModelRequest {

        data class ForProjectScript(val enclosingProject: Project) : ScriptModelRequest()

        object ForScriptPlugin : ScriptModelRequest()
    }

    private
    fun scriptModelRequestFor(project: Project): ScriptModelRequest =
        targetScriptFileForModelRequestOf(project).let {
            when (it) {
                null -> ScriptModelRequest.ForProjectScript(project)
                else -> projectFor(it, project)
                    ?.let { ScriptModelRequest.ForProjectScript(it) }
                    ?: ScriptModelRequest.ForScriptPlugin
            }
        }

    private
    data class ScriptClassPath(val bin: ClassPath, val src: ClassPath)

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
    fun implicitImportsOf(project: Project) =
        project.serviceOf<ImplicitImports>().list

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
    fun compilationClassPathOf(project: Project) =
        kotlinScriptClassPathProviderOf(project)
            .compilationClassPathOf(classLoaderScopeOf(project))

    private
    fun classLoaderScopeOf(project: Project) =
        (project as ProjectInternal).classLoaderScope

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
