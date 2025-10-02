/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.TargetTypeInformation
import org.gradle.api.reflect.TypeOf
import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.declarative.dsl.schema.ContainerElementFactory
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.dataOfTypeOrNull
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.kotlin.dsl.accessors.ContainerElementFactoryEntry
import org.gradle.kotlin.dsl.accessors.ProjectFeatureEntry
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.software.internal.ProjectFeatureRegistry
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

data class KotlinDslDclSchema(
    val containerElementFactories: List<ContainerElementFactoryEntry<TypeOf<*>>>,
    val projectFeatures: List<ProjectFeatureEntry<TypeOf<*>>>
)

/**
 * A utility on top of [KotlinDslDclSchemaCollector] that encapsulates the preparation of the arguments and
 * the collection of the DCL schema parts that are relevant to Kotlin DSL.
 */
internal fun KotlinDslDclSchemaCollector.collectDclSchemaForKotlinDslTarget(target: Any, targetScope: ClassLoaderScope): KotlinDslDclSchema? {
    fun projectTypeRegistryOf(target: Any): ProjectFeatureRegistry? =
        when (target) {
            is Project -> target.serviceOf<ProjectFeatureRegistry>()
            is Settings -> target.serviceOf<ProjectFeatureRegistry>()
            else -> null
        }

    fun dclInterpretationSequenceFor(target: Any): InterpretationSequence? {
        val schemaBuildingResult = when (target) {
            is Project -> target.serviceOf<InterpretationSchemaBuilder>().getEvaluationSchemaForScript(DeclarativeScriptContext.ProjectScript)
            is SettingsInternal -> target.serviceOf<InterpretationSchemaBuilder>().getEvaluationSchemaForScript(DeclarativeScriptContext.SettingsScript)
            else -> null
        }
        return (schemaBuildingResult as? InterpretationSchemaBuildingResult.InterpretationSequenceAvailable)?.sequence
    }

    val containerElementFactories = dclInterpretationSequenceFor(target)?.let { interpretationSequence ->
        collectContainerFactories(interpretationSequence, targetScope)
    } ?: return null

    val projectTypes = projectTypeRegistryOf(target)?.let(::collectProjectTypes) ?: return null

    return KotlinDslDclSchema(containerElementFactories, projectTypes)
}


@ServiceScope(Scope.UserHome::class)
internal interface KotlinDslDclSchemaCollector {
    fun collectContainerFactories(interpretationSequence: InterpretationSequence, classLoaderScope: ClassLoaderScope): List<ContainerElementFactoryEntry<TypeOf<*>>>
    fun collectProjectTypes(projectFeatureRegistry: ProjectFeatureRegistry): List<ProjectFeatureEntry<TypeOf<*>>>
}

internal class CachedKotlinDslDclSchemaCollector(
    private val cache: KotlinDslDclSchemaCache,
    private val delegate: KotlinDslDclSchemaCollector
) : KotlinDslDclSchemaCollector {
    override fun collectContainerFactories(interpretationSequence: InterpretationSequence, classLoaderScope: ClassLoaderScope): List<ContainerElementFactoryEntry<TypeOf<*>>> =
        cache.getOrPutContainerElementFactories(interpretationSequence, classLoaderScope) { delegate.collectContainerFactories(interpretationSequence, classLoaderScope) }

    override fun collectProjectTypes(projectFeatureRegistry: ProjectFeatureRegistry): List<ProjectFeatureEntry<TypeOf<*>>> =
        cache.getOrPutContainerElementProjectTypes(projectFeatureRegistry) { delegate.collectProjectTypes(projectFeatureRegistry) }
}

internal class DefaultKotlinDslDclSchemaCollector : KotlinDslDclSchemaCollector {
    override fun collectContainerFactories(interpretationSequence: InterpretationSequence, classLoaderScope: ClassLoaderScope): List<ContainerElementFactoryEntry<TypeOf<*>>> {
        val classLoader = classLoaderScope.localClassLoader

        return interpretationSequence.steps.flatMap { step ->
            val analysisSchema = step.evaluationSchemaForStep.analysisSchema
            val typeRefContext = SchemaTypeRefContext(analysisSchema)
            val types = analysisSchema.dataClassTypesByFqName.values.filterIsInstance<DataClass>()
            types.flatMap { type ->
                type.memberFunctions.mapNotNull { function ->
                    function.metadata.dataOfTypeOrNull<ContainerElementFactory>()?.let { factory ->
                        val containerType = typeOf(type, classLoader)
                            ?: return@let null
                        val elementDclType = typeRefContext.resolveRef(factory.elementType) as? DataClass
                            ?: return@let null
                        val elementType = typeOf(elementDclType, classLoader)
                            ?: return@let null
                        ContainerElementFactoryEntry<TypeOf<*>>(function.simpleName, containerType, elementType)
                    }
                }
            }
        }
    }

    private fun typeOf(dclDataClass: DataClass, classLoader: ClassLoader): TypeOf<*>? {
        fun loadClassOrNull(className: String): Class<*>? = try {
            classLoader.loadClass(className)
        } catch (_: ClassNotFoundException) {
            null
        }

        val loadedClass = loadClassOrNull(dclDataClass.javaTypeName)
            ?: return null

        val typeArgs = dclDataClass.javaTypeArgumentTypeNames.map {
            loadClassOrNull(it) ?: return null
        }

        return if (typeArgs.isNotEmpty()) {
            parameterizedTypeOfRawGenericClass(typeArgs.map(::TypeProjection), loadedClass)
        } else{
            TypeOf.typeOf(loadedClass)
        }
    }

    override fun collectProjectTypes(projectFeatureRegistry: ProjectFeatureRegistry): List<ProjectFeatureEntry<TypeOf<*>>> =
        projectFeatureRegistry.projectFeatureImplementations.entries.map { (name, implementation) ->
            val targetType = when (val target = implementation.targetDefinitionType) {
                is TargetTypeInformation.DefinitionTargetTypeInformation ->  TypeOf.typeOf(target.definitionType)
                is TargetTypeInformation.BuildModelTargetTypeInformation<*> ->
                    parameterizedTypeOfRawGenericClass(listOf(TypeProjection(target.buildModelType, TypeProjectionKind.OUT)), Definition::class.java)
                else -> error("Unexpected target type $target")
            }
            ProjectFeatureEntry(name, TypeOf.typeOf(implementation.definitionPublicType), targetType)
        }

    /**
     * Workaround: The [TypeOf] infrastructure handles parameterized types specially.
     * Passing the raw [Class] obtained from the class loader to [TypeOf.parameterizedTypeOf] would not work.
     * We need to provide a [ParameterizedType] instance.
     */
    private fun parameterizedTypeOfRawGenericClass(typeArgs: List<TypeProjection>, loadedClass: Class<*>): TypeOf<Any> =
        TypeOf.typeOf(object : ParameterizedType {
            override fun getActualTypeArguments(): Array<Type> = typeArgs.map { (clazz, projection) ->
                when (projection) {
                    TypeProjectionKind.NONE -> clazz
                    TypeProjectionKind.OUT -> object : WildcardType {
                        override fun getUpperBounds(): Array<out Type> = arrayOf(clazz)
                        override fun getLowerBounds() = emptyArray<Type>()
                    }
                    TypeProjectionKind.IN -> object : WildcardType {
                        override fun getUpperBounds(): Array<out Type> = emptyArray()
                        override fun getLowerBounds() = arrayOf(clazz)
                    }
                }
            }.toTypedArray<Type>()
            override fun getRawType(): Type = loadedClass

            /** [Class.getNestHost] is @since 11, cannot use it; but we are fine with no owner type here. */
            /** [Class.getNestHost] is @since 11, cannot use it; but we are fine with no owner type here. */
            override fun getOwnerType() = null
        })

    private data class TypeProjection(val clazz: Class<*>, val projection: TypeProjectionKind = TypeProjectionKind.NONE)

    private enum class TypeProjectionKind {
        NONE, OUT, IN
    }
}
