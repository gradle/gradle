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
            """$licenseHeader

package org.gradle.script.lang.kotlin

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency

import org.gradle.api.artifacts.dsl.DependencyHandler

${configurationExtensions()}""")
    }

    private fun configurationExtensions(): String =
        project
            .configurations
            .joinToString(separator = "\n\n") {
                extensionsFor(it.name)
            }

    private fun extensionsFor(name: String): String =
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
*
* @see DependencyHandler.add
*/
fun DependencyHandler.$name(dependencyNotation: Any): Dependency =
    add("$name", dependencyNotation)

/**
* Adds a dependency to the '$name' configuration.
*
* @param dependencyNotation notation for the dependency to be added.
* @param dependencyConfiguration expression to use to configure the dependency.
* @return The dependency.
*
* @see DependencyHandler.add
*/
inline fun DependencyHandler.$name(
    dependencyNotation: String,
    dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
    add("$name", dependencyNotation, dependencyConfiguration)

/**
* Adds a dependency to the '$name' configuration.
*
* @param group the group of the module to be added as a dependency.
* @param name the name of the module to be added as a dependency.
* @param version the optional version of the module to be added as a dependency.
* @param configuration the optional configuration of the module to be added as a dependency.
* @param classifier the optional classifier of the module artifact to be added as a dependency.
* @param ext the optional extension of the module artifact to be added as a dependency.
* @return The dependency.
*
* @see DependencyHandler.add
*/
fun DependencyHandler.$name(
    group: String,
    name: String,
    version: String? = null,
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null): ExternalModuleDependency =
    create(group, name, version, configuration, classifier, ext).apply { add("$name", this) }

/**
* Adds a dependency to the '$name' configuration.
*
* @param group the group of the module to be added as a dependency.
* @param name the name of the module to be added as a dependency.
* @param version the optional version of the module to be added as a dependency.
* @param configuration the optional configuration of the module to be added as a dependency.
* @param classifier the optional classifier of the module artifact to be added as a dependency.
* @param ext the optional extension of the module artifact to be added as a dependency.
* @param dependencyConfiguration expression to use to configure the dependency.
* @return The dependency.
*
* @see DependencyHandler.create
* @see DependencyHandler.add
*/
inline fun DependencyHandler.$name(
    group: String,
    name: String,
    version: String? = null,
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null,
    dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
    add("$name", create(group, name, version, configuration, classifier, ext), dependencyConfiguration)

/**
* Adds a dependency to the '$name' configuration.
*
* @param dependency dependency to be added.
* @param dependencyConfiguration expression to use to configure the dependency.
* @return The dependency.
*
* @see DependencyHandler.add
*/
inline fun <T : ModuleDependency> DependencyHandler.$name(dependency: T, dependencyConfiguration: T.() -> Unit): T =
    add("$name", dependency, dependencyConfiguration)
    """
}
