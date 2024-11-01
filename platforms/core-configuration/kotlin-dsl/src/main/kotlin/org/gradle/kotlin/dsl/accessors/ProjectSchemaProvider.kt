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

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.kotlin.dsl.*
import java.io.Serializable


@ServiceScope(Scope.UserHome::class)
interface ProjectSchemaProvider {

    fun schemaFor(scriptTarget: Any, classLoaderScope: ClassLoaderScope): TypedProjectSchema?
}


data class SchemaType(val value: TypeOf<*>) {

    companion object {
        inline fun <reified T> of() = SchemaType(typeOf<T>())
    }

    val kotlinString = kotlinTypeStringFor(value)

    override fun toString(): String = kotlinString
}


typealias TypedProjectSchema = ProjectSchema<SchemaType>


data class ProjectSchema<out T>(
    val extensions: List<ProjectSchemaEntry<T>>,
    val conventions: List<ProjectSchemaEntry<T>>,
    val tasks: List<ProjectSchemaEntry<T>>,
    val containerElements: List<ProjectSchemaEntry<T>>,
    val configurations: List<ConfigurationEntry<String>>,
    val modelDefaults: List<ProjectSchemaEntry<T>>,
    val containerElementFactories: List<ContainerElementFactoryEntry<T>>,
    val softwareTypeEntries: List<SoftwareTypeEntry<T>>,
    val scriptTarget: Any? = null
) {

    fun <U> map(f: (T) -> U) = ProjectSchema(
        extensions.map { it.map(f) },
        conventions.map { it.map(f) },
        tasks.map { it.map(f) },
        containerElements.map { it.map(f) },
        configurations,
        modelDefaults.map { it.map(f) },
        containerElementFactories.map { it.map(f) },
        softwareTypeEntries.map { it.map(f) },
        scriptTarget
    )

    fun isNotEmpty(): Boolean =
        extensions.isNotEmpty()
            || conventions.isNotEmpty()
            || tasks.isNotEmpty()
            || containerElements.isNotEmpty()
            || configurations.isNotEmpty()
            || modelDefaults.isNotEmpty()
            || containerElementFactories.isNotEmpty()
}


data class ProjectSchemaEntry<out T>(
    val target: T,
    val name: String,
    val type: T
) : Serializable {

    fun <U> map(f: (T) -> U) =
        ProjectSchemaEntry(f(target), name, f(type))
}


data class ConfigurationEntry<T>(
    val target: T,
    val dependencyDeclarationAlternatives: List<String> = listOf()
) : Serializable {

    fun hasDeclarationDeprecations() = dependencyDeclarationAlternatives.isNotEmpty()

    fun <U> map(f: (T) -> U) =
        ConfigurationEntry(f(target), dependencyDeclarationAlternatives)
}


data class ContainerElementFactoryEntry<out T>(
    val factoryName: String,
    val containerReceiverType: T,
    val publicType: T
) : Serializable {

    fun <U> map(f: (T) -> U) =
        ContainerElementFactoryEntry(factoryName, f(containerReceiverType), f(publicType))
}

data class SoftwareTypeEntry<out T>(
    val softwareTypeName: String,
    val modelType: T,
) : Serializable {

    fun <U> map(f: (T) -> U) =
        SoftwareTypeEntry(softwareTypeName, f(modelType))
}
