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

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.File

open class GenerateClasspathManifest : DefaultTask() {

    var outputDirectory: File? = null

    @Input
    val compileOnly = project.configurations.getByName("compileOnly")!!

    @Input
    val runtime = project.configurations.getByName("runtime")!!

    val outputFile: File
        @OutputFile
        get() = File(outputDirectory!!, "${project.name}-classpath.properties")

    @TaskAction
    fun generate() {
        val projects = join(compileOnly.dependencies.filterIsInstance<ExternalModuleDependency>().map { it.name })
        val runtime = join(runtime.files.map { it.name })
        write("projects=$projects\nruntime=$runtime\n")
    }

    private fun join(ss: List<String>) =
        ss.joinToString(separator = ",")

    private fun write(text: String) {
        outputFile.writeText(text)
    }
}
