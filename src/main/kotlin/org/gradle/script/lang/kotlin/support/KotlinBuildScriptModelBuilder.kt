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

package org.gradle.script.lang.kotlin.support

import org.gradle.script.lang.kotlin.provider.KotlinScriptClassPathProvider

import org.gradle.tooling.provider.model.ToolingModelBuilder

import org.gradle.api.Project

import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.internal.classloader.ClasspathUtil

import java.io.File
import java.io.Serializable

interface KotlinBuildScriptModel {
    val classPath: List<File>
}

object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.script.lang.kotlin.support.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, project: Project): Any =
        StandardKotlinBuildScriptModel(scriptCompilationClassPathOf(project))

    private fun scriptCompilationClassPathOf(project: Project): List<File> =
        (scriptClassPathOf(project)
        + buildSrcClassPathOf(project)
        + gradleScriptKotlinApiOf(project))

    private fun scriptClassPathOf(project: Project) =
        (project.buildscript as ScriptHandlerInternal).scriptClassPath.asFiles

    private fun buildSrcClassPathOf(project: Project) =
        ClasspathUtil
            .getClasspath(project.buildscript.classLoader)
            .asFiles
            .filter { it.isDirectory }
}

class StandardKotlinBuildScriptModel(override val classPath: List<File>) : KotlinBuildScriptModel, Serializable

internal
fun gradleScriptKotlinApiOf(project: Project): List<File> =
    kotlinScriptClassPathProviderOf(project).run {
        gradleApi.asFiles + gradleScriptKotlinJars.asFiles
    }

internal
fun kotlinScriptClassPathProviderOf(project: Project) =
    (project as ProjectInternal).services[KotlinScriptClassPathProvider::class.java]!!
