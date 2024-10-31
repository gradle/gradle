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
import org.gradle.api.reflect.TypeOf
import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.declarative.dsl.schema.ContainerElementFactory
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.dataOfTypeOrNull
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.kotlin.dsl.accessors.ContainerElementFactoryEntry
import org.gradle.kotlin.dsl.accessors.SoftwareTypeEntry
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

data class KotlinDslDclSchema(
    val containerElementFactories: List<ContainerElementFactoryEntry<TypeOf<*>>>,
    val softwareTypes: List<SoftwareTypeEntry<TypeOf<*>>>
)

/**
 * A utility on top of [KotlinDslDclSchemaCollector] that encapsulates the preparation of the arguments and
 * the collection of the DCL schema parts that are relevant to Kotlin DSL.
 */
internal fun KotlinDslDclSchemaCollector.collectDclSchemaForKotlinDslTarget(target: Any, targetScope: ClassLoaderScope): KotlinDslDclSchema? {
    fun softwareTypeRegistryOf(target: Any): SoftwareTypeRegistry? =
        when (target) {
            is Project -> target.serviceOf<SoftwareTypeRegistry>()
            is Settings -> target.serviceOf<SoftwareTypeRegistry>()
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

    val softwareTypes = softwareTypeRegistryOf(target)?.let(::collectSoftwareTypes) ?: return null

    return KotlinDslDclSchema(containerElementFactories, softwareTypes)
}


internal interface KotlinDslDclSchemaCollector {
    fun collectContainerFactories(interpretationSequence: InterpretationSequence, classLoaderScope: ClassLoaderScope): List<ContainerElementFactoryEntry<TypeOf<*>>>
    fun collectSoftwareTypes(softwareTypeRegistry: SoftwareTypeRegistry): List<SoftwareTypeEntry<TypeOf<*>>>
}

internal class CachedKotlinDslDclSchemaCollector(
    private val cache: KotlinDslDclSchemaCache,
    private val delegate: KotlinDslDclSchemaCollector
) : KotlinDslDclSchemaCollector {
    override fun collectContainerFactories(interpretationSequence: InterpretationSequence, classLoaderScope: ClassLoaderScope): List<ContainerElementFactoryEntry<TypeOf<*>>> =
        cache.getOrPutContainerElementFactories(interpretationSequence, classLoaderScope) { delegate.collectContainerFactories(interpretationSequence, classLoaderScope) }

    override fun collectSoftwareTypes(softwareTypeRegistry: SoftwareTypeRegistry): List<SoftwareTypeEntry<TypeOf<*>>> =
        cache.getOrPutContainerElementSoftwareTypes(softwareTypeRegistry) { delegate.collectSoftwareTypes(softwareTypeRegistry) }
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
            parameterizedTypeOfRawGenericClass(typeArgs, loadedClass)
        } else{
            TypeOf.typeOf(loadedClass)
        }
    }

    override fun collectSoftwareTypes(softwareTypeRegistry: SoftwareTypeRegistry): List<SoftwareTypeEntry<TypeOf<*>>> =
        softwareTypeRegistry.softwareTypeImplementations.entries.map { (name, implementation) ->
            SoftwareTypeEntry(name, TypeOf.typeOf(implementation.modelPublicType))
        }

    /**
     * Workaround: The [TypeOf] infrastructure handles parameterized types specially.
     * Passing the raw [Class] obtained from the class loader to [TypeOf.parameterizedTypeOf] would not work.
     * We need to provide a [ParameterizedType] instance.
     */
    private fun parameterizedTypeOfRawGenericClass(typeArgs: List<Class<*>>, loadedClass: Class<*>): TypeOf<Any> =
        TypeOf.typeOf(object : ParameterizedType {
            override fun getActualTypeArguments(): Array<Type> = typeArgs.toTypedArray<Type>()
            override fun getRawType(): Type = loadedClass

            /** [Class.getNestHost] is @since 11, cannot use it; but we are fine with no owner type here. */
            /** [Class.getNestHost] is @since 11, cannot use it; but we are fine with no owner type here. */
            override fun getOwnerType() = null
        })
}
