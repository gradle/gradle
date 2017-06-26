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

import org.gradle.api.reflect.TypeOf
import org.gradle.api.reflect.TypeOf.typeOf

import java.io.File
import java.io.Serializable


internal
data class ProjectSchema<out T>(
    val extensions: Map<String, T>,
    val conventions: Map<String, T>,
    val configurations: List<String>) : Serializable {

    fun <U> map(f: (T) -> U) =
        ProjectSchema(
            extensions.mapValues { f(it.value) },
            conventions.mapValues { f(it.value) },
            configurations.toList())
}


internal
fun multiProjectKotlinStringSchemaFor(root: Project): Map<String, ProjectSchema<String>> =
    multiProjectSchemaFor(root).mapValues { it.value.withKotlinTypeStrings() }


internal
fun multiProjectSchemaFor(root: Project): Map<String, ProjectSchema<TypeOf<*>>> =
    root.allprojects.map { it.path to schemaFor(it) }.toMap()


internal
fun schemaFor(project: Project): ProjectSchema<TypeOf<*>> =
    accessibleProjectSchemaFrom(
        project.extensions.schema,
        project.convention.plugins,
        project.configurations.names.toList())


internal
fun accessibleProjectSchemaFrom(
    extensionSchema: Map<String, TypeOf<*>>,
    conventionPlugins: Map<String, Any>,
    configurationNames: List<String>): ProjectSchema<TypeOf<*>> =

    ProjectSchema(
        extensions = extensionSchema
            .filterKeys(::isPublic)
            .filterValues(::isAccessible),
        conventions = conventionPlugins
            .filterKeys(::isPublic)
            .mapValues { typeOf(it.value::class.java) }
            .filterValues(::isAccessible),
        configurations = configurationNames
            .filter(::isPublic))


internal
fun isPublic(name: String): Boolean =
    !name.startsWith("_")


internal
fun isAccessible(type: TypeOf<*>): Boolean =
    type.run {
        when {
            isParameterized -> isAccessible(parameterizedTypeDefinition) && actualTypeArguments.all(::isAccessible)
            isArray -> isAccessible(componentType)
            isSynthetic -> false
            else -> isPublic
        }
    }


internal
fun toJson(multiProjectStringSchema: Map<String, ProjectSchema<String>>): String =
    toJson(multiProjectStringSchema)


internal
fun ProjectSchema<TypeOf<*>>.withKotlinTypeStrings() =
    map(::kotlinTypeStringFor)


@Suppress("unchecked_cast")
internal
fun loadMultiProjectSchemaFrom(file: File) =
    (groovy.json.JsonSlurper().parse(file) as Map<String, Map<String, *>>).mapValues {
        ProjectSchema(
            extensions = it.value["extensions"] as? Map<String, String> ?: emptyMap(),
            conventions = it.value["conventions"] as? Map<String, String> ?: emptyMap(),
            configurations = it.value["configurations"] as? List<String> ?: emptyList())
    }


internal
fun kotlinTypeStringFor(type: TypeOf<*>): String =
    type.run {
        when {
            isArray ->
                "Array<${kotlinTypeStringFor(componentType)}>"
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

