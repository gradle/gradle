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

package org.gradle.kotlin.dsl.accessors

import groovy.json.JsonOutput.toJson

import org.gradle.api.Project
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionsSchema

import org.gradle.api.reflect.TypeOf
import org.gradle.kotlin.dsl.getPluginByName

import java.io.File
import java.io.Serializable


internal
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


internal
data class ProjectSchemaEntry<out T>(
    val target: T,
    val name: String,
    val type: T
) : Serializable {

    fun <U> map(f: (T) -> U) =
        ProjectSchemaEntry(f(target), name, f(type))
}


internal
fun multiProjectKotlinStringSchemaFor(root: Project): Map<String, ProjectSchema<String>> =
    multiProjectSchemaFor(root).mapValues { it.value.withKotlinTypeStrings() }


private
fun multiProjectSchemaFor(root: Project): Map<String, ProjectSchema<TypeOf<*>>> =
    root.allprojects.map { it.path to schemaFor(it) }.toMap()


internal
fun schemaFor(project: Project): ProjectSchema<TypeOf<*>> =
    targetSchemaFor(project, TypeOf.typeOf(Project::class.java)).let { targetSchema ->
        ProjectSchema(
            targetSchema.extensions,
            targetSchema.conventions,
            accessibleConfigurations(project.configurations.names.toList()))
    }


private
data class ExtensionConventionSchema(
    val extensions: List<ProjectSchemaEntry<TypeOf<*>>>,
    val conventions: List<ProjectSchemaEntry<TypeOf<*>>>
)


private
fun targetSchemaFor(target: Any, targetType: TypeOf<*>): ExtensionConventionSchema {
    val extensions = mutableListOf<ProjectSchemaEntry<TypeOf<*>>>()
    val conventions = mutableListOf<ProjectSchemaEntry<TypeOf<*>>>()
    if (target is ExtensionAware) {
        accessibleExtensionsSchema(target.extensions.extensionsSchema).forEach { schema ->
            val schemaEntry = ProjectSchemaEntry(targetType, schema.name, schema.publicType)
            extensions.add(schemaEntry)
            if (!schema.isDeferredConfigurable) {
                targetSchemaFor(target.extensions.getByName(schema.name), schema.publicType).let { nestedSchema ->
                    extensions += nestedSchema.extensions
                    conventions += nestedSchema.conventions
                }
            }
        }
    }
    if (target is HasConvention) {
        accessibleConventionsSchema(target.convention.plugins).forEach { (name, type) ->
            val schemaEntry = ProjectSchemaEntry<TypeOf<*>>(targetType, name, type)
            conventions.add(schemaEntry)
            targetSchemaFor(target.convention.getPluginByName(name), type).let { nestedSchema ->
                extensions += nestedSchema.extensions
                conventions += nestedSchema.conventions
            }
        }
    }
    return ExtensionConventionSchema(extensions.distinct(), conventions.distinct())
}


private
fun accessibleExtensionsSchema(extensionsSchema: ExtensionsSchema) =
    extensionsSchema.filter { isPublic(it.name) }


private
fun accessibleConventionsSchema(plugins: Map<String, Any>) =
    plugins.filterKeys(::isPublic).mapValues { TypeOf.typeOf(it.value::class.java) }


internal
fun accessibleConfigurations(configurations: List<String>) =
    configurations.filter(::isPublic)


internal
fun isPublic(name: String): Boolean =
    !name.startsWith("_")


internal
fun toJson(multiProjectStringSchema: Map<String, ProjectSchema<String>>): String =
    toJson(multiProjectStringSchema)


internal
fun ProjectSchema<TypeOf<*>>.withKotlinTypeStrings() =
    map(::kotlinTypeStringFor)


@Suppress("unchecked_cast")
internal
fun loadMultiProjectSchemaFrom(file: File) =
    (groovy.json.JsonSlurper().parse(file) as Map<String, Map<String, *>>).mapValues { (_, value) ->
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


internal
fun kotlinTypeStringFor(type: TypeOf<*>): String =
    type.run {
        when {
            isArray ->
                "Array<${kotlinTypeStringFor(componentType!!)}>"
            isParameterized ->
                "$parameterizedTypeDefinition<${actualTypeArguments.joinToString(transform = ::kotlinTypeStringFor)}>"
            isWildcard ->
                upperBound?.let(::kotlinTypeStringFor) ?: "Any"
            else ->
                toString().let { primitiveTypeStrings[it] ?: it }
        }
    }


private
val primitiveTypeStrings =
    mapOf(
        "java.lang.Object" to "Any",
        "java.lang.String" to "String",
        "java.lang.Character" to "Char",
        "char" to "Char",
        "java.lang.Boolean" to "Boolean",
        "boolean" to "Boolean",
        "java.lang.Byte" to "Byte",
        "byte" to "Byte",
        "java.lang.Short" to "Short",
        "short" to "Short",
        "java.lang.Integer" to "Int",
        "int" to "Int",
        "java.lang.Long" to "Long",
        "long" to "Long",
        "java.lang.Float" to "Float",
        "float" to "Float",
        "java.lang.Double" to "Double",
        "double" to "Double")


internal
val primitiveKotlinTypeNames = primitiveTypeStrings.values.toHashSet()
