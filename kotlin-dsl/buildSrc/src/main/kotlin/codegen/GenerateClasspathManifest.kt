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
import org.gradle.api.artifacts.ExternalModuleDependency

import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.*

import java.io.File


open class GenerateClasspathManifest : DefaultTask() {

    @get:OutputDirectory
    var outputDirectory: File? = null

    @get:InputFiles
    val compileOnly by project.configurations

    @get:InputFiles
    val runtime by project.configurations

    @get:Internal
    val outputFile by lazy {
        File(outputDirectory!!, "${moduleName()}-classpath.properties")
    }

    @Suppress("unused")
    @TaskAction
    fun generate() {
        val projects = join(compileOnly.dependencies.filterIsInstance<ExternalModuleDependency>().map { it.name })
        val runtime = join(runtime.files.map { it.name })
        write("projects=$projects\nruntime=$runtime\n")
    }

    private
    fun join(ss: List<String>) =
        ss.joinToString(separator = ",")

    private
    fun write(text: String) {
        outputFile.writeText(text)
    }

    private
    fun moduleName(): String =
        project.base.archivesBaseName
}
