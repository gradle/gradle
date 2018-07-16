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

package org.gradle.kotlin.dsl.provider.plugins

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.reflect.HasPublicType
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSet

import org.gradle.kotlin.dsl.accessors.ProjectSchema
import org.gradle.kotlin.dsl.accessors.ProjectSchemaEntry
import org.gradle.kotlin.dsl.accessors.ProjectSchemaProvider


class DefaultProjectSchemaProvider : ProjectSchemaProvider {

    override fun schemaFor(project: Project): ProjectSchema<TypeOf<*>> =
        targetSchemaFor(project, typeOfProject).let { targetSchema ->
            ProjectSchema(
                targetSchema.extensions,
                targetSchema.conventions,
                accessibleConfigurationsOf(project))
        }
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

    fun collectSchemaOf(target: Any, targetType: TypeOf<*>) {
        if (target is ExtensionAware) {
            accessibleExtensionsSchema(target.extensions.extensionsSchema).forEach { schema ->
                extensions.add(ProjectSchemaEntry(targetType, schema.name, schema.publicType))
                if (!schema.isDeferredConfigurable) {
                    collectSchemaOf(target.extensions.getByName(schema.name), schema.publicType)
                }
            }
        }
        if (target is Project) {
            accessibleConventionsSchema(target.convention.plugins).forEach { name, type ->
                conventions.add(ProjectSchemaEntry(targetType, name, type))
                collectSchemaOf(target.convention.plugins[name]!!, type)
            }
            sourceSetsOf(target)?.forEach { sourceSet ->
                collectSchemaOf(sourceSet, typeOfSourceSet)
            }
        }
    }

    collectSchemaOf(target, targetType)

    return ExtensionConventionSchema(extensions.distinct(), conventions.distinct())
}


private
fun accessibleExtensionsSchema(extensionsSchema: ExtensionsSchema) =
    extensionsSchema.filter { isPublic(it.name) }


private
fun accessibleConventionsSchema(plugins: Map<String, Any>) =
    plugins.filterKeys(::isPublic).mapValues { inferPublicTypeOfConvention(it.value) }


private
fun sourceSetsOf(project: Project) =
    project.convention.findPlugin(JavaPluginConvention::class.java)?.sourceSets


private
fun inferPublicTypeOfConvention(instance: Any) =
    if (instance is HasPublicType) instance.publicType
    else TypeOf.typeOf(instance::class.java.firstNonSyntheticOrSelf)


private
val Class<*>.firstNonSyntheticOrSelf
    get() = firstNonSyntheticOrNull ?: this


private
val Class<*>.firstNonSyntheticOrNull: Class<*>?
    get() = takeIf { !isSynthetic } ?: superclass?.firstNonSyntheticOrNull


private
fun accessibleConfigurationsOf(project: Project) =
    project.configurations.names.filter(::isPublic)


private
fun isPublic(name: String): Boolean =
    !name.startsWith("_")


private
val typeOfProject = typeOf<Project>()


private
val typeOfSourceSet = typeOf<SourceSet>()


internal
inline fun <reified T> typeOf(): TypeOf<T> =
    object : TypeOf<T>() {}
