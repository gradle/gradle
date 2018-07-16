/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.gradle.api.Project
import org.gradle.api.reflect.TypeOf

import java.io.File
import java.io.Serializable


interface ProjectSchemaProvider {

    fun schemaFor(project: Project): ProjectSchema<TypeOf<*>>

    fun multiProjectSchemaFor(root: Project): Map<String, ProjectSchema<TypeOf<*>>> =
        root.allprojects.map { it.path to schemaFor(it) }.toMap()
}


data class ProjectSchema<out T>(
    val extensions: List<ProjectSchemaEntry<T>>,
    val conventions: List<ProjectSchemaEntry<T>>,
    val configurations: List<String>
) : Serializable {

    fun <U> map(f: (T) -> U) =
        ProjectSchema(
            extensions.map { it.map(f) },
            conventions.map { it.map(f) },
            configurations.toList())
}


data class ProjectSchemaEntry<out T>(
    val target: T,
    val name: String,
    val type: T
) : Serializable {

    fun <U> map(f: (T) -> U) =
        ProjectSchemaEntry(f(target), name, f(type))
}


fun ProjectSchema<TypeOf<*>>.withKotlinTypeStrings() =
    map(::kotlinTypeStringFor)


fun toJson(multiProjectStringSchema: Map<String, ProjectSchema<String>>): String =
    JsonOutput.toJson(multiProjectStringSchema)


@Suppress("unchecked_cast")
fun loadMultiProjectSchemaFrom(file: File) =
    (JsonSlurper().parse(file) as Map<String, Map<String, *>>).mapValues { (_, value) ->
        ProjectSchema(
            extensions = loadSchemaEntryListFrom(value["extensions"]),
            conventions = loadSchemaEntryListFrom(value["conventions"]),
            configurations = value["configurations"] as? List<String> ?: emptyList())
    }


@Suppress("unchecked_cast")
private
fun loadSchemaEntryListFrom(extensions: Any?): List<ProjectSchemaEntry<String>> =
    when (extensions) {
        is Map<*, *> -> // <0.17 format
            (extensions as? Map<String, String>)?.map {
                ProjectSchemaEntry(
                    Project::class.java.name,
                    it.key,
                    it.value)
            } ?: emptyList()
        is List<*> -> // >=0.17 format
            (extensions as? List<Map<String, String>>)?.map {
                ProjectSchemaEntry(
                    it.getValue("target"),
                    it.getValue("name"),
                    it.getValue("type"))
            } ?: emptyList()
        else -> emptyList()
    }
