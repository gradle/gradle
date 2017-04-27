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

package org.gradle.script.lang.kotlin.resolver

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.internal.classloader.ClasspathUtil

import org.gradle.script.lang.kotlin.accessors.accessorsClassPathFor
import org.gradle.script.lang.kotlin.provider.KotlinScriptClassPathProvider
import org.gradle.script.lang.kotlin.support.serviceOf

import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.io.File
import java.io.Serializable


interface KotlinBuildScriptModel {
    val classPath: List<File>
    val  sourcePath: List<File>
}


internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.script.lang.kotlin.resolver.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, project: Project): Any {
        val classPath = classPathFrom(project)
        val sourcePath = sourcePathFor(classPath, project)
        return StandardKotlinBuildScriptModel(classPath, sourcePath)
    }

    private
    fun classPathFrom(project: Project): List<File> {
        val targetBuildscriptFile = targetBuildscriptFile(project)
        return when {
            targetBuildscriptFile != null ->
                projectFor(targetBuildscriptFile, project)
                    ?.let { targetProject -> buildScriptClassPathOf(targetProject) }
                    ?: scriptPluginClassPathOf(project)
            else ->
                buildScriptClassPathOf(project)
        }
    }

    private
    fun sourcePathFor(classPath: List<File>, project: Project) =
        SourcePathProvider.sourcePathFor(classPath, project.rootProject.projectDir, project.gradle.gradleHomeDir)

    private
    fun targetBuildscriptFile(project: Project) =
        project.findProperty(kotlinBuildScriptModelTarget)?.let { canonicalFile(it as String) }

    private
    fun projectFor(targetBuildscriptFile: File?, project: Project) =
        project.allprojects.find { it.buildFile == targetBuildscriptFile }

    private
    fun canonicalFile(path: String): File = File(path).canonicalFile

    private
    fun buildScriptClassPathOf(project: Project): List<File> =
        compilationClassPathOf(project)
            .let { it + accessorsClassPathFor(project, it) }
            .asFiles

    private
    fun compilationClassPathOf(project: Project) =
        kotlinScriptClassPathProviderOf(project)
            .compilationClassPathOf(classLoaderScopeOf(project))

    private
    fun classLoaderScopeOf(project: Project) =
        (project as ProjectInternal).classLoaderScope

    private
    fun scriptPluginClassPathOf(project: Project) =
        buildSrcClassPathOf(project) + gradleScriptKotlinApiOf(project)

    private
    fun buildSrcClassPathOf(project: Project) =
        ClasspathUtil
            .getClasspath(project.buildscript.classLoader)
            .asFiles
            .filter { it.name == "buildSrc.jar" }
}


data class StandardKotlinBuildScriptModel(
    override val classPath: List<File>,
    override val sourcePath: List<File>) : KotlinBuildScriptModel, Serializable


internal
fun gradleScriptKotlinApiOf(project: Project): List<File> =
    kotlinScriptClassPathProviderOf(project).run {
        gradleScriptKotlinApi.asFiles
    }


internal
fun kotlinScriptClassPathProviderOf(project: Project) =
    project.serviceOf<KotlinScriptClassPathProvider>()
