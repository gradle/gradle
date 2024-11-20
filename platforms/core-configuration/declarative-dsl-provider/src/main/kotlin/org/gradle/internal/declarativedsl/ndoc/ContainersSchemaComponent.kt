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

package org.gradle.internal.declarativedsl.ndoc

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.Internal
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DeclarativeDslInterpretationException
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.SchemaItemMetadataInternal.SchemaMemberOriginInternal.DefaultContainerElementFactory
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.InstanceAndPublicType
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.nullInstanceAndPublicType
import org.gradle.internal.declarativedsl.mappingToJvm.withFallbackPublicType
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.annotationsWithGetters
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import org.gradle.internal.declarativedsl.utils.DclContainerMemberExtractionUtils.elementFactoryFunctionNameFromElementType
import org.gradle.internal.declarativedsl.utils.DclContainerMemberExtractionUtils.elementTypeFromNdocContainerType
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure


internal fun EvaluationSchemaBuilder.namedDomainObjectContainers() {
    val component = ContainersSchemaComponent()

    registerAnalysisSchemaComponent(component)
    ifConversionSupported {
        registerObjectConversionComponent(component)
    }
}


internal class ContainersSchemaComponent : AnalysisSchemaComponent, ObjectConversionComponent {
    private val containerByAccessorId = mutableMapOf<String, ContainerProperty>()
    private val elementFactoryFunctions = hashSetOf<SchemaMemberFunction>()

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        // For subtypes of NDOC<T>, generate the element factory function as a member:
        object : FunctionExtractor {
            override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex) =
                if (kClass.isSubclassOf(NamedDomainObjectContainer::class) && kClass != NamedDomainObjectContainer::class) {
                    val elementType = elementTypeFromNdocContainerType(kClass.starProjectedType)
                    if (elementType != null) {
                        listOf(newElementFactoryFunction(kClass.toDataTypeRef(), elementType, inContext = kClass))
                    } else emptyList()
                } else emptyList()
        },

        // For all types, if they have an NDOC-typed property, add a configuring function for it. Also, maybe generate a synthetic type
        // for NDOC<T> (if it is not a real subtype of NDOC<T>) which will be the configuring function's receiver:
        object : FunctionExtractor {
            override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
                val containerProperties = containerProperties(kClass)

                containerProperties.forEach { containerProperty ->
                    containerByAccessorId[containerProperty.accessorId()] = containerProperty
                }

                return containerProperties.map { containerProperty ->
                    val containerType = containerProperty.containerType
                    val containerTypeRef = if (containerType.classifier == NamedDomainObjectContainer::class) {
                        val typeId = ndocTypeId(containerProperty.originDeclaration.callable, containerProperty.elementType)
                        preIndex.getOrRegisterSyntheticType(typeId, containerProperty::generateSyntheticContainerType).ref
                    } else containerType.toDataTypeRef()
                        ?: throw DeclarativeDslInterpretationException("Cannot use $containerType as a container type in ${containerProperty.originDeclaration.callable}")

                    containerProperty.containerConfiguringFunction(kClass, containerTypeRef)
                }
            }
        }
    )


    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        object : TypeDiscovery {
            override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<KClass<*>> =
                containerProperties(kClass).flatMap { property ->
                    listOfNotNull(
                        property.elementType.classifier, // the element type
                        property.containerType.classifier.takeIf { it != NamedDomainObjectContainer::class } // the container type, if it is a proper subtype of NDOC<T>
                    )
                }.filterIsInstance<KClass<*>>()
        }
    )

    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        object : RuntimeCustomAccessors {
            override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
                val callable = containerByAccessorId[accessor.customAccessorIdentifier]?.originDeclaration?.callable ?: return nullInstanceAndPublicType
                return callable.call(receiverObject) to callable.returnType.jvmErasure
            }
        }
    )

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(
        object : RuntimeFunctionResolver {
            override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction): RuntimeFunctionResolver.Resolution =
                // TODO: this check relies on the hashing+equality implementation of the schema functions; if those get proxied (e.g. to TAPI), it won't work; We do not need that for now, though.
                if (schemaFunction in elementFactoryFunctions) {
                    RuntimeFunctionResolver.Resolution.Resolved(object : DeclarativeRuntimeFunction {
                        override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
                            val result = (receiver as NamedDomainObjectContainer<*>).maybeCreate(binding.values.single() as String)
                            val resultInstanceAndPublicType = withFallbackPublicType(result)
                            return DeclarativeRuntimeFunction.InvocationResult(resultInstanceAndPublicType, resultInstanceAndPublicType)
                        }
                    })
                } else RuntimeFunctionResolver.Resolution.Unresolved
        }
    )

    private fun newElementFactoryFunction(receiverTypeRef: DataTypeRef, elementKType: KType, inContext: Any) =
        elementFactoryFunction(receiverTypeRef, elementKType, inContext).also(elementFactoryFunctions::add)

    private inner class ContainerProperty(
        val ownerType: KClass<*>,
        val name: String,
        val containerType: KType,
        val elementType: KType,
        val originDeclaration: ContainerPropertyDeclaration
    ) {
        fun syntheticTypeName() = DefaultFqName.parse(NamedDomainObjectContainer::class.qualifiedName!! + "\$of\$" + elementTypeName().replace(".", "_"))

        private fun elementTypeName() = dataTypeRefName(elementType.classifier!!)

        private fun syntheticContainerTypeRef() = DataTypeRefInternal.DefaultName(syntheticTypeName())

        fun containerConfiguringFunction(ownerType: KClass<*>, containerTypeRef: DataTypeRef) = DefaultDataMemberFunction(
            ownerType.toDataTypeRef(),
            name,
            emptyList(),
            false,
            FunctionSemanticsInternal.DefaultAccessAndConfigure(
                ConfigureAccessorInternal.DefaultCustom(containerTypeRef, accessorId()),
                DefaultUnit,
                DefaultRequired
            )
        )

        fun accessorId() = "container:${dataTypeRefName(ownerType)}:$name"

        fun generateSyntheticContainerType(): DataClass = DefaultDataClass(
            syntheticTypeName(),
            NamedDomainObjectContainer::class.java.name,
            listOfNotNull((elementType.classifier as? KClass<*>)?.java?.name),
            emptySet(),
            emptyList(),
            listOf(newElementFactoryFunction(syntheticContainerTypeRef(), elementType, inContext = originDeclaration.callable)),
            emptyList()
        )
    }

    sealed interface ContainerPropertyDeclaration {
        val callable: KCallable<*>
            get() = when (this) {
                is KotlinProperty -> property
                is Getter -> getter
            }

        data class KotlinProperty(val property: KProperty<*>) : ContainerPropertyDeclaration
        data class Getter(val getter: KFunction<*>) : ContainerPropertyDeclaration
    }

    private fun containerProperties(kClass: KClass<*>): List<ContainerProperty> {
        val propertiesFromMemberProperties = kClass.memberProperties.filter(::isPublicAndNotInternal).mapNotNull {
            val elementType = elementTypeFromNdocContainerType(it.returnType) ?: return@mapNotNull null
            ContainerProperty(kClass, it.propertyName(), it.returnType, elementType, ContainerPropertyDeclaration.KotlinProperty(it))
        }
        val propertiesFromMemberFunctions = kClass.memberFunctions.filter(::isPublicAndNotInternal).mapNotNull {
            val elementType = elementTypeFromNdocContainerType(it.returnType) ?: return@mapNotNull null
            ContainerProperty(kClass, it.propertyName(), it.returnType, elementType, ContainerPropertyDeclaration.Getter(it))
        }

        return (propertiesFromMemberProperties + propertiesFromMemberFunctions).distinctBy { it.name }
    }

    private fun ndocTypeId(context: KCallable<*>, elementType: KType): String {
        val elementTypeName = elementType.classifier?.let { dataTypeRefName(it) } ?: throw DeclarativeDslInterpretationException("Non-class container element type $elementType in $context")
        return "\$ndocOf_$elementTypeName"
    }

    private fun KCallable<*>.propertyName() = when (this) {
        is KProperty<*> -> name
        else -> name.substringAfter("get").replaceFirstChar { it.lowercase(Locale.getDefault()) }
    }
}

private fun elementFactoryFunction(
    receiverTypeRef: DataTypeRef,
    elementType: KType,
    context: Any
): DataMemberFunction {
    val elementTypeRef = elementType.toDataTypeRef() as? DataTypeRef.Name
        ?: throw DeclarativeDslInterpretationException("Cannot use $elementType as container element type in $context")

    val elementFactoryName = elementFactoryFunctionNameFromElementType(elementType)

    return DefaultDataMemberFunction(
        receiverTypeRef,
        elementFactoryName,
        listOf(
            DefaultDataParameter("name", DataTypeInternal.DefaultStringDataType.ref, false, ParameterSemanticsInternal.DefaultIdentityKey(null))
        ),
        false,
        FunctionSemanticsInternal.DefaultAccessAndConfigure(
            ConfigureAccessorInternal.DefaultConfiguringLambdaArgument(elementTypeRef),
            FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultConfiguredObject,
            DefaultRequired
        ),
        metadata = listOf(DefaultContainerElementFactory(elementTypeRef))
    )
}


private fun dataTypeRefName(it: KClassifier) = (it.toDataTypeRef() as DataTypeRef.Name).fqName.toString()

private fun isPublicAndNotInternal(member: KCallable<*>): Boolean =
    member.visibility == KVisibility.PUBLIC &&
        member.annotationsWithGetters.none { it is Internal } // TODO: @Internal might not be the best fit, as it is more related to the task models
