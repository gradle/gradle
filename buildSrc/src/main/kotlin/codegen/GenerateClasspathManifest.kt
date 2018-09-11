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

package codegen

import accessors.base

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.*


@CacheableTask
open class GenerateClasspathManifest : DefaultTask() {

    @get:OutputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val outputDirectory = project.objects.directoryProperty()

    @get:Input
    val compileOnlyProjectNames by lazy {
        val compileOnly by project.configurations
        join(
            compileOnly.dependencies.asSequence()
                .filterIsInstance<ExternalModuleDependency>()
                .map { it.name })
    }

    @get:Input
    val runtimeFileNames by lazy {
        val runtime by project.configurations
        join(runtime.files.asSequence().map { it.name })
    }

    private
    val outputFile by lazy {
        outputDirectory.file("${project.moduleName}-classpath.properties").get().asFile
    }

    @Suppress("unused")
    @TaskAction
    fun generate() {
        write("projects=$compileOnlyProjectNames\nruntime=$runtimeFileNames\n")
    }

    private
    fun join(ss: Sequence<String>) =
        ss.joinToString(separator = ",")

    private
    fun write(text: String) {
        outputFile.writeText(text)
    }

    private
    val Project.moduleName
        get() = base.archivesBaseName
}
