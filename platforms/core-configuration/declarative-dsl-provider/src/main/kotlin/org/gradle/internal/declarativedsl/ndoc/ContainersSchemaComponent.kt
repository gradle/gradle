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
import org.gradle.declarative.dsl.schema.CustomAccessorIdentifier.ContainerAccessorIdentifier
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DeclarativeDslInterpretationException
import org.gradle.internal.declarativedsl.analysis.DefaultContainerAccessorIdentifier
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
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.MemberKind
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingTags
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass
import org.gradle.internal.declarativedsl.schemaBuilder.annotationsWithGetters
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import org.gradle.internal.declarativedsl.schemaBuilder.toKType
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import org.gradle.internal.declarativedsl.ndoc.DclContainerMemberExtractionUtils.elementFactoryFunctionNameFromElementType
import org.gradle.internal.declarativedsl.ndoc.DclContainerMemberExtractionUtils.elementTypeFromNdocContainerType
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
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
    private val containerByAccessorId = mutableMapOf<ContainerAccessorIdentifier, ContainerProperty>()
    private val elementFactoryFunctions = hashSetOf<SchemaMemberFunction>()
    private val elementPublicTypes = mutableMapOf<SchemaMemberFunction, KClass<*>>()

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        // For subtypes of NDOC<T>, generate the element factory function as a member:
        object : FunctionExtractor {
            override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
                if (kClass.isSubclassOf(NamedDomainObjectContainer::class) && kClass != NamedDomainObjectContainer::class) {
                    val elementType = elementTypeFromNdocContainerType(kClass.starProjectedType)
                    if (elementType != null) {
                        host.withTag(SchemaBuildingTags.elementTypeOfContainerSubtype(kClass)) {
                            listOf(newElementFactoryFunction(host, host.containerTypeRef(kClass), elementType, kClass))
                        }
                    } else emptyList()
                } else emptyList()
        },

        // For all types, if they have an NDOC-typed property, add a configuring function for it. Also, maybe generate a synthetic type
        // for NDOC<T> (if it is not a real subtype of NDOC<T>) which will be the configuring function's receiver:
        object : FunctionExtractor {
            override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
                val containerProperties = containerProperties(host, kClass)

                containerProperties.forEach { containerProperty ->
                    containerByAccessorId[containerProperty.accessorId(host)] = containerProperty
                }

                return containerProperties.map { containerProperty ->
                    host.withTag(SchemaBuildingTags.namedDomainObjectContainer(containerProperty.name)) {
                        val containerType = containerProperty.containerType
                        val containerTypeRef = if (containerType.classifier == NamedDomainObjectContainer::class) {
                            val typeId = ndocTypeId(host, containerProperty.elementType)
                            preIndex.getOrRegisterSyntheticType(typeId) { containerProperty.generateSyntheticContainerType(host) }.ref
                        } else host.inContextOfModelMember(containerProperty.originDeclaration.callable) {
                            host.modelTypeRef(containerType.toKType())
                        }

                        containerProperty.containerConfiguringFunction(host, kClass, containerTypeRef)
                    }
                }
            }
        }
    )


    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        object : TypeDiscovery {
            override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<DiscoveredClass> =
                containerProperties(typeDiscoveryServices.host, kClass).flatMap { property ->
                    listOfNotNull(
                        // discover the element type, only if the declared container type is not NDOC<T>; otherwise, it will be discovered from the signature
                        (property.containerType.takeIf { it.classifier != NamedDomainObjectContainer::class })
                            ?.let { DiscoveredClass.classesOf(it, DiscoveryTag.ContainerElement(property.originDeclaration.callable)) }
                    ).flatten()
                }
        }
    )

    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        object : RuntimeCustomAccessors {
            override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
                val callable = containerByAccessorId[accessor.accessorIdentifier]?.originDeclaration?.callable ?: return InstanceAndPublicType.NULL
                return InstanceAndPublicType.of(callable.call(receiverObject), callable.returnType.jvmErasure)
            }
        }
    )

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(
        object : RuntimeFunctionResolver {
            override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction, scopeClassLoader: ClassLoader): RuntimeFunctionResolver.Resolution =
                // TODO: this check relies on the hashing+equality implementation of the schema functions; if those get proxied (e.g. to TAPI), it won't work; We do not need that for now, though.
                if (schemaFunction in elementFactoryFunctions) {
                    RuntimeFunctionResolver.Resolution.Resolved(object : DeclarativeRuntimeFunction {
                        override fun callBy(receiver: Any?, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
                            val result = (receiver as NamedDomainObjectContainer<*>).maybeCreate(binding.values.single() as String)
                            val resultInstanceAndPublicType = InstanceAndPublicType.of(result, elementPublicTypes[schemaFunction])
                            return DeclarativeRuntimeFunction.InvocationResult(resultInstanceAndPublicType, resultInstanceAndPublicType)
                        }
                    })
                } else RuntimeFunctionResolver.Resolution.Unresolved
        }
    )

    private fun newElementFactoryFunction(host: SchemaBuildingHost, receiverTypeRef: DataTypeRef, elementKType: KType, inContext: Any): DataMemberFunction {
        val elementFactoryFunction = elementFactoryFunction(host, receiverTypeRef, elementKType, inContext)
        elementFactoryFunctions.add(elementFactoryFunction)
        elementPublicTypes[elementFactoryFunction] = elementKType.jvmErasure
        return elementFactoryFunction
    }

    private inner class ContainerProperty(
        val ownerType: KClass<*>,
        val name: String,
        val containerType: SupportedTypeProjection.SupportedType,
        val elementType: SupportedTypeProjection.SupportedType,
        val originDeclaration: ContainerPropertyDeclaration
    ) {
        fun syntheticTypeName(host: SchemaBuildingHost) =
            DefaultFqName.parse(NamedDomainObjectContainer::class.qualifiedName!! + "\$of\$" + elementTypeName(host).replace(".", "_"))

        private fun elementTypeName(host: SchemaBuildingHost) = dataTypeRefName(host, elementType.classifier as KClass<*>)

        private fun syntheticContainerTypeRef(host: SchemaBuildingHost) = DataTypeRefInternal.DefaultName(syntheticTypeName(host))

        fun containerConfiguringFunction(host: SchemaBuildingHost, ownerType: KClass<*>, containerTypeRef: DataTypeRef) = DefaultDataMemberFunction(
            host.containerTypeRef(ownerType),
            name,
            emptyList(),
            false,
            FunctionSemanticsInternal.DefaultAccessAndConfigure(
                ConfigureAccessorInternal.DefaultContainer(containerTypeRef, accessorId(host)),
                DefaultUnit,
                containerTypeRef,
                DefaultRequired
            )
        )

        fun accessorId(host: SchemaBuildingHost) = DefaultContainerAccessorIdentifier(name, dataTypeRefName(host, ownerType))

        fun generateSyntheticContainerType(host: SchemaBuildingHost): DataClass = DefaultDataClass(
            syntheticTypeName(host),
            NamedDomainObjectContainer::class.java.name,
            listOfNotNull((elementType.classifier as? KClass<*>)?.java?.name),
            emptySet(),
            emptyList(),
            listOf(newElementFactoryFunction(host, syntheticContainerTypeRef(host), elementType.toKType(), inContext = originDeclaration.callable)),
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

    private fun containerProperties(host: SchemaBuildingHost, kClass: KClass<*>): List<ContainerProperty> {
        val members = host.classMembers(kClass).potentiallyDeclarativeMembers
            .filter { member -> member.kCallable.annotationsWithGetters.none { it is Internal } }

        val propertiesFromMemberProperties = members.mapNotNull {
            if (it.kind != MemberKind.READ_ONLY_PROPERTY) return@mapNotNull null

            val elementType = elementTypeFromNdocContainerType(it.returnType) ?: return@mapNotNull null
            ContainerProperty(kClass, it.name, it.returnType, elementType, ContainerPropertyDeclaration.KotlinProperty(it.kCallable as KProperty<*>))
        }
        val propertiesFromMemberFunctions = members.mapNotNull {
            if (it.kind != MemberKind.FUNCTION || it.parameters.isNotEmpty()) return@mapNotNull null

            val elementType = elementTypeFromNdocContainerType(it.returnType) ?: return@mapNotNull null
            ContainerProperty(kClass, it.kCallable.propertyName(), it.returnType, elementType, ContainerPropertyDeclaration.Getter(it.kCallable as KFunction<*>))
        }

        return (propertiesFromMemberProperties + propertiesFromMemberFunctions).distinctBy { it.name }
    }

    private fun ndocTypeId(host: SchemaBuildingHost, elementType: SupportedTypeProjection.SupportedType): String {
        val elementTypeName = dataTypeRefName(host, elementType.classifier as KClass<*>)
        return "\$ndocOf_$elementTypeName"
    }

    private fun KCallable<*>.propertyName() = when (this) {
        is KProperty<*> -> name
        else -> name.substringAfter("get").replaceFirstChar { it.lowercase(Locale.getDefault()) }
    }
}

private fun elementFactoryFunction(
    host: SchemaBuildingHost,
    receiverTypeRef: DataTypeRef,
    elementType: KType,
    context: Any
): DataMemberFunction {
    val elementTypeRef = host.modelTypeRef(elementType) as? DataTypeRef.Name
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
            elementTypeRef,
            DefaultRequired
        ),
        metadata = listOf(DefaultContainerElementFactory(elementTypeRef))
    )
}


private fun dataTypeRefName(
    host: SchemaBuildingHost,
    it: KClass<*>
) = (host.modelTypeRef(it.starProjectedType) as DataTypeRef.Name).fqName.toString()

