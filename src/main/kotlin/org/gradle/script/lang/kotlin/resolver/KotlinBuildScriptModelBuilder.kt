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
import org.gradle.internal.classpath.ClassPath

import org.gradle.script.lang.kotlin.accessors.additionalSourceFilesForBuildscriptOf
import org.gradle.script.lang.kotlin.provider.CachingKotlinCompiler
import org.gradle.script.lang.kotlin.provider.KotlinScriptClassPathProvider
import org.gradle.script.lang.kotlin.support.exportClassPathFromHierarchyOf

import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.io.File
import java.io.Serializable


interface KotlinBuildScriptModel {
    val classPath: List<File>
}


internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.script.lang.kotlin.resolver.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, project: Project): Any =
        StandardKotlinBuildScriptModel(classPathFrom(project))

    private fun classPathFrom(project: Project): List<File> {
        val targetBuildscriptFile = targetBuildscriptFile(project)
        return when {
            targetBuildscriptFile != null ->
                projectFor(targetBuildscriptFile, project)
                    ?.let { targetProject -> scriptCompilationClassPathOf(targetProject) }
                    ?: scriptPluginClassPathOf(project)
            else ->
                scriptCompilationClassPathOf(project)
        }
    }

    private fun targetBuildscriptFile(project: Project) =
        project.findProperty(kotlinBuildScriptModelTarget)?.let { canonicalFile(it as String) }

    private fun projectFor(targetBuildscriptFile: File?, project: Project) =
        project.allprojects.find { it.buildFile == targetBuildscriptFile }

    private fun canonicalFile(path: String): File = File(path).canonicalFile

    private fun scriptCompilationClassPathOf(project: Project): List<File> {
        val accessorsCompilationClassPath =
            exportClassPathFromHierarchyOf(classLoaderScopeOf(project)) + gradleScriptKotlinApiOf(project)
        return accessorsCompilationClassPath.asFiles + compiledAccessorsFor(project, accessorsCompilationClassPath)
    }

    private fun classLoaderScopeOf(project: Project) =
        (project as ProjectInternal).classLoaderScope

    private fun compiledAccessorsFor(project: Project, classPath: ClassPath): List<File> =
        additionalSourceFilesForBuildscriptOf(project)
            .takeIf { it.isNotEmpty() }
            ?.let { compiledLibFrom(it, classPath, project) }
            ?.let { listOf(it) }
            ?: emptyList()

    private fun compiledLibFrom(sourceFiles: List<File>, classPath: ClassPath, project: Project) =
        try {
            cachingKotlinCompilerOf(project).compileLib(sourceFiles, classPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    private fun cachingKotlinCompilerOf(project: Project) =
        project.serviceOf<CachingKotlinCompiler>()

    private fun scriptPluginClassPathOf(project: Project) =
        buildSrcClassPathOf(project) + gradleScriptKotlinApiOf(project)

    private fun buildSrcClassPathOf(project: Project) =
        ClasspathUtil
            .getClasspath(project.buildscript.classLoader)
            .asFiles
            .filter { it.name == "buildSrc.jar" }
}


data class StandardKotlinBuildScriptModel(
    override val classPath: List<File>) : KotlinBuildScriptModel, Serializable


internal
fun gradleScriptKotlinApiOf(project: Project): List<File> =
    kotlinScriptClassPathProviderOf(project).run {
        gradleApi.asFiles + gradleScriptKotlinJars.asFiles
    }


internal
fun kotlinScriptClassPathProviderOf(project: Project) =
    project.serviceOf<KotlinScriptClassPathProvider>()


internal
inline fun <reified T : Any> Project.serviceOf(): T =
    (this as ProjectInternal).services[T::class.java]!!
