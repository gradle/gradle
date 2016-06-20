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
*
* @see DependencyHandler.add
*/
fun KotlinDependencyHandler.$name(dependencyNotation: Any) =
    add("$name", dependencyNotation)
    """
}
