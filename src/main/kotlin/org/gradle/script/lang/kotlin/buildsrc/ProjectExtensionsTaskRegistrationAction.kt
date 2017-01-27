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

package org.gradle.script.lang.kotlin.buildsrc

import groovy.json.JsonOutput.prettyPrint
import groovy.json.JsonOutput.toJson

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.configuration.project.ProjectConfigureAction

import org.gradle.script.lang.kotlin.buildsrc.ProjectExtensionsBuildSrcConfigurationAction.Companion.PROJECT_SCHEMA_RESOURCE_PATH

import org.gradle.script.lang.kotlin.*

import java.io.File


class ProjectExtensionsTaskRegistrationAction : ProjectConfigureAction {

    override fun execute(project: ProjectInternal) {
        with (project) {
            if (this == rootProject) {
                tasks {
                    "gskGenerateExtensions"(GenerateProjectSchema::class) {
                        destinationFile = file("buildSrc/$PROJECT_SCHEMA_RESOURCE_PATH")
                    }
                }
            }
            tasks {
                "gskProjectExtensions"(DisplayExtensions::class)
            }
        }
    }
}


open class DisplayExtensions : DefaultTask() {

    override fun getGroup() =
        "help"

    override fun getDescription() =
        "Displays the Kotlin code for accessing the available project extensions and conventions."

    @Suppress("unused")
    @TaskAction
    fun printExtensions() {
        schemaFor(project).run {
            extensions.forEach { (name, type) ->
                printProjectExtension(name, type, "extension", "extensions.getByName")
            }
            conventions.forEach { (name, type) ->
                if (name !in extensions) {
                    printProjectExtension(name, type, "convention", "convention.getPluginByName")
                }
            }
        }
    }

    private fun printProjectExtension(name: String, type: Class<*>, kind: String, getter: String) {
        val typeString = kotlinTypeStringFor(type)
        println()
        println("""
            /**
             * Retrieves or configures the [$name][$typeString] project $kind.
             */
            fun Project.$name(configuration: $typeString.() -> Unit = {}) =
                $getter<$typeString>("$name").apply(configuration)
        """.replaceIndent())
        println()
    }
}


internal
data class ProjectSchema<out T>(
    val extensions: Map<String, T>,
    val conventions: Map<String, T>) {

    fun <U> map(f: (T) -> U) =
        ProjectSchema(
            extensions.mapValues { f(it.value) },
            conventions.mapValues { f(it.value) })
}


internal
fun multiProjectSchemaFor(root: Project): Map<String, ProjectSchema<Class<*>>> =
    root.allprojects.map { it.path to schemaFor(it) }.toMap()


private
fun schemaFor(project: Project) =
    ProjectSchema(
        extensions = project.extensions.schema,
        conventions = project.convention.plugins.mapValues { it.value.javaClass })


private
fun kotlinTypeStringFor(clazz: Class<*>) =
    clazz.kotlin.qualifiedName!!


open class GenerateProjectSchema : DefaultTask() {

    @get:OutputFile
    var destinationFile: File? = null

    @Suppress("unused")
    @TaskAction
    fun generateProjectSchema() {
        val schema = multiProjectSchemaFor(project).mapValues { it.value.map(::kotlinTypeStringFor) }
        destinationFile!!.writeText(
            prettyPrint(toJson(schema)))
    }
}
