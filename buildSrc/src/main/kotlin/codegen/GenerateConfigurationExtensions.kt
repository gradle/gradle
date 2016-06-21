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
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.File

open class GenerateConfigurationExtensions : DefaultTask() {

    @get:OutputFile
    var outputFile: File? = null

    @TaskAction
    fun generate() {
        outputFile!!.writeText(
            """package org.gradle.script.lang.kotlin

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

${configurationExtensions()}""")
    }

    private fun configurationExtensions(): String =
        project
            .configurations
            .map { extensionsFor(it.name) }
            .joinToString(separator = "\n\n")

    private fun  extensionsFor(name: String): String =
    """
/**
 * The '$name' configuration.
 */
val ConfigurationContainer.$name: Configuration
    get() = getByName("$name")

/**
* Adds a dependency to the '$name' configuration.
*
* @param dependencyNotation notation for the dependency to be added.
* @return The dependency.
* @see DependencyHandler.add
*/
fun KotlinDependencyHandler.$name(dependencyNotation: Any) =
    add("$name", dependencyNotation)
    """
}
