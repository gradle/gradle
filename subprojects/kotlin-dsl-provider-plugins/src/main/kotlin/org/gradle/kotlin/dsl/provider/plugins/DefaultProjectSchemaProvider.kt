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

import org.gradle.api.NamedDomainObjectCollectionSchema
import org.gradle.api.NamedDomainObjectCollectionSchema.NamedDomainObjectSchema
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.HasPublicType
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.Factory
import org.gradle.internal.deprecation.DeprecatableConfiguration
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.accessors.ConfigurationEntry
import org.gradle.kotlin.dsl.accessors.ProjectSchema
import org.gradle.kotlin.dsl.accessors.ProjectSchemaEntry
import org.gradle.kotlin.dsl.accessors.ProjectSchemaProvider
import org.gradle.kotlin.dsl.accessors.SchemaType
import org.gradle.kotlin.dsl.accessors.TypedProjectSchema
import java.lang.reflect.Modifier
import kotlin.reflect.KVisibility


class DefaultProjectSchemaProvider : ProjectSchemaProvider {

    override fun schemaFor(project: Project): TypedProjectSchema =
        targetSchemaFor(project, typeOfProject).let { targetSchema ->
            ProjectSchema(
                targetSchema.extensions,
                targetSchema.conventions,
                targetSchema.tasks,
                targetSchema.containerElements,
                accessibleConfigurationsOf(project)
            ).map(::SchemaType)
        }
}


internal
data class TargetTypedSchema(
    val extensions: List<ProjectSchemaEntry<TypeOf<*>>>,
    val conventions: List<ProjectSchemaEntry<TypeOf<*>>>,
    val tasks: List<ProjectSchemaEntry<TypeOf<*>>>,
    val containerElements: List<ProjectSchemaEntry<TypeOf<*>>>
)


internal
fun targetSchemaFor(target: Any, targetType: TypeOf<*>): TargetTypedSchema {

    val extensions = mutableListOf<ProjectSchemaEntry<TypeOf<*>>>()
    val conventions = mutableListOf<ProjectSchemaEntry<TypeOf<*>>>()
    val tasks = mutableListOf<ProjectSchemaEntry<TypeOf<*>>>()
    val containerElements = mutableListOf<ProjectSchemaEntry<TypeOf<*>>>()

    fun collectSchemaOf(target: Any, targetType: TypeOf<*>) {
        if (target is ExtensionAware) {
            accessibleContainerSchema(target.extensions.extensionsSchema).forEach { schema ->
                extensions.add(ProjectSchemaEntry(targetType, schema.name, schema.publicType))
                collectSchemaOf(target.extensions.getByName(schema.name), schema.publicType)
            }
        }
        if (target is Project) {
            @Suppress("deprecation")
            val plugins: Map<String, Any> = DeprecationLogger.whileDisabled(Factory { target.convention.plugins })!!
            accessibleConventionsSchema(plugins).forEach { (name, type) ->
                conventions.add(ProjectSchemaEntry(targetType, name, type))
                val plugin = DeprecationLogger.whileDisabled(Factory { plugins[name] })!!
                collectSchemaOf(plugin, type)
            }
            accessibleContainerSchema(target.tasks.collectionSchema).forEach { schema ->
                tasks.add(ProjectSchemaEntry(typeOfTaskContainer, schema.name, schema.publicType))
            }
            collectSchemaOf(target.dependencies, typeOfDependencyHandler)
            collectSchemaOf(target.repositories, typeOfRepositoryHandler)
            // WARN eagerly realize all source sets
            sourceSetsOf(target)?.forEach { sourceSet ->
                collectSchemaOf(sourceSet, typeOfSourceSet)
            }
        }
        if (target is NamedDomainObjectContainer<*>) {
            accessibleContainerSchema(target.collectionSchema).forEach { schema ->
                containerElements.add(ProjectSchemaEntry(targetType, schema.name, schema.publicType))
            }
        }
    }

    collectSchemaOf(target, targetType)

    return TargetTypedSchema(
        extensions,
        conventions,
        tasks,
        containerElements
    )
}


private
fun accessibleConventionsSchema(plugins: Map<String, Any>) =
    DeprecationLogger.whileDisabled(Factory {
        plugins.filterKeys(::isPublic).mapValues { inferPublicTypeOfConvention(it.value) }
    })!!


private
fun accessibleContainerSchema(collectionSchema: NamedDomainObjectCollectionSchema) =
    collectionSchema.elements
        .filter { isPublic(it.name) }
        .map(NamedDomainObjectSchema::toFirstKotlinPublicOrSelf)


private
fun NamedDomainObjectSchema.toFirstKotlinPublicOrSelf() =
    publicType.concreteClass.let { schemaType ->
        // Because a public Java class might not correspond necessarily to a
        // public Kotlin type due to Kotlin `internal` semantics, we check
        // whether the public Java class is also the first public Kotlin type,
        // otherwise we compute a new schema entry with the correct Kotlin type.
        val firstPublicKotlinType = schemaType.firstPublicKotlinAccessorTypeOrSelf
        when {
            firstPublicKotlinType === schemaType -> this
            else -> ProjectSchemaNamedDomainObjectSchema(
                name,
                TypeOf.typeOf(firstPublicKotlinType)
            )
        }
    }


internal
val Class<*>.firstPublicKotlinAccessorTypeOrSelf: Class<*>
    get() = firstPublicKotlinAccessorType ?: this


private
val Class<*>.firstPublicKotlinAccessorType: Class<*>?
    get() = accessorTypePrecedenceSequence().find { it.isKotlinPublic }


internal
fun Class<*>.accessorTypePrecedenceSequence(): Sequence<Class<*>> = sequence {

    // First, all the classes in the hierarchy, subclasses before superclasses
    val classes = ancestorClassesIncludingSelf.toList()
    yieldAll(classes)

    // Then all supported interfaces sorted by subtyping (subtypes before supertypes)
    val interfaces = mutableListOf<Class<*>>()
    classes.forEach { `class` ->
        `class`.interfaces.forEach { `interface` ->
            when (val indexOfSupertype = interfaces.indexOfFirst { it.isAssignableFrom(`interface`) }) {
                -1 -> interfaces.add(`interface`)
                else -> if (interfaces[indexOfSupertype] != `interface`) {
                    interfaces.add(indexOfSupertype, `interface`)
                }
            }
        }
    }
    yieldAll(interfaces)
}


internal
val Class<*>.ancestorClassesIncludingSelf: Sequence<Class<*>>
    get() = sequence {

        yield(this@ancestorClassesIncludingSelf)

        var superclass: Class<*>? = superclass
        while (superclass != null) {
            val thisSuperclass: Class<*> = superclass
            val nextSuperclass = thisSuperclass.superclass
            if (nextSuperclass != null) { // skip java.lang.Object
                yield(thisSuperclass)
            }
            superclass = nextSuperclass
        }
    }


private
val Class<*>.isKotlinPublic: Boolean
    get() = isKotlinVisible && kotlin.visibility == KVisibility.PUBLIC


private
val Class<*>.isKotlinVisible: Boolean
    get() = isPublic && !isLocalClass && !isAnonymousClass && !isSynthetic


private
val Class<*>.isPublic
    get() = Modifier.isPublic(modifiers)


private
class ProjectSchemaNamedDomainObjectSchema(
    private val objectName: String,
    private val objectPublicType: TypeOf<*>
) : NamedDomainObjectSchema {

    override fun getName() = objectName

    override fun getPublicType() = objectPublicType
}


private
fun sourceSetsOf(project: Project) =
    project.extensions.findByName("sourceSets") as? SourceSetContainer


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
    project.configurations
        .filter { isPublic(it.name) }
        .map(::toConfigurationEntry)


private
fun toConfigurationEntry(configuration: Configuration) = (configuration as DeprecatableConfiguration).run {
    ConfigurationEntry(name, declarationAlternatives ?: listOf())
}


private
fun isPublic(name: String): Boolean =
    !name.startsWith("_")


private
val typeOfProject = typeOf<Project>()


private
val typeOfSourceSet = typeOf<SourceSet>()


private
val typeOfDependencyHandler = typeOf<DependencyHandler>()


private
val typeOfRepositoryHandler = typeOf<RepositoryHandler>()


private
val typeOfTaskContainer = typeOf<TaskContainer>()


internal
inline fun <reified T> typeOf(): TypeOf<T> =
    object : TypeOf<T>() {}
