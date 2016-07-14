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

import org.gradle.script.lang.kotlin.extra

import org.gradle.internal.classpath.ClassPath

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
    val modelBuilderRegistry: ToolingModelBuilderRegistry) : Plugin<Project>, ToolingModelBuilder {

    override fun apply(project: Project) {
        modelBuilderRegistry.register(this)
    }

    override fun canBuild(modelName: String): Boolean =
        modelName == KotlinBuildScriptModel::class.qualifiedName

    override fun buildAll(modelName: String, project: Project): Any =
        StandardKotlinBuildScriptModel(scriptClassPathOf(project))

    private fun scriptClassPathOf(project: Project) =
        project.kotlinScriptClassPath.asFiles
}

class StandardKotlinBuildScriptModel(override val classPath: List<File>) : KotlinBuildScriptModel, Serializable

var Project.kotlinScriptClassPath: ClassPath
    get() = extra.get(kotlinScriptClassPathPropertyName) as ClassPath
    set(value) = extra.set(kotlinScriptClassPathPropertyName, value)

private val kotlinScriptClassPathPropertyName = "org.gradle.script.lang.kotlin.classpath"
