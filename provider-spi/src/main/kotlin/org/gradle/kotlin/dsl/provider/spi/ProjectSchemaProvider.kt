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

package org.gradle.kotlin.dsl.provider.spi

import org.gradle.api.Project
import org.gradle.api.reflect.TypeOf

import java.io.Serializable


interface ProjectSchemaProvider {

    fun schemaFor(project: Project): ProjectSchema<TypeOf<*>>
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
