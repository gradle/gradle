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
import org.gradle.declarative.dsl.schema.CustomAccessorIdentifier.ContainerAccessorIdentifier
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
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
import org.gradle.internal.declarativedsl.ndoc.DclContainerMemberExtractionUtils.elementFactoryFunctionNameFromElementType
import org.gradle.internal.declarativedsl.ndoc.DclContainerMemberExtractionUtils.elementTypeFromNdocContainerType
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractionMetadata
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.LossySchemaBuildingOperation
import org.gradle.internal.declarativedsl.schemaBuilder.MemberKind
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingTags
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedCallable
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag
import org.gradle.internal.declarativedsl.schemaBuilder.annotationsWithGetters
import org.gradle.internal.declarativedsl.schemaBuilder.asSupported
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import org.gradle.internal.declarativedsl.schemaBuilder.orError
import org.gradle.internal.declarativedsl.schemaBuilder.orFailWith
import org.gradle.internal.declarativedsl.schemaBuilder.schemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.toKType
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
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
            override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): List<FunctionExtractionResult> =
                if (kClass.isSubclassOf(NamedDomainObjectContainer::class) && kClass != NamedDomainObjectContainer::class) {
                    val elementType = elementTypeFromNdocContainerType(host, kClass.starProjectedType.asSupported(host).orFailWith {
                        return listOf(ExtractionResult.of(it, FunctionExtractionMetadata(emptyList())))
                    })
                    if (elementType != null) {
                        val containerTypeRef = host.containerTypeRef(kClass)
                            .orFailWith { return listOf(ExtractionResult.of(it, FunctionExtractionMetadata(emptyList()))) }

                        host.withTag(SchemaBuildingTags.elementTypeOfContainerSubtype(kClass)) {
                            listOf(ExtractionResult.of(newElementFactoryFunction(host, containerTypeRef, elementType), FunctionExtractionMetadata(emptyList())))
                        }
                    } else emptyList()
                } else emptyList()
        },

        // For all types, if they have an NDOC-typed property, add a configuring function for it. Also, maybe generate a synthetic type
        // for NDOC<T> (if it is not a real subtype of NDOC<T>) which will be the configuring function's receiver:
        object : FunctionExtractor {
            override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): List<FunctionExtractionResult> {
                val containerProperties = containerProperties(host, kClass)

                containerProperties.forEach { containerProperty ->
                    containerByAccessorId[containerProperty.accessorId(host)] = containerProperty
                }

                return containerProperties.map { containerProperty ->
                    host.withTag(SchemaBuildingTags.namedDomainObjectContainer(containerProperty.name)) {
                        val containerType = containerProperty.containerType
                        val containerTypeRef = if (containerType.classifier == NamedDomainObjectContainer::class) {
                            val typeId = ndocTypeId(host, containerProperty.elementType)
                            val syntheticContainerType = containerProperty.generateSyntheticContainerType(host)
                                .orFailWith { return@withTag it }
                            preIndex.getOrRegisterSyntheticType(typeId) { syntheticContainerType }.ref
                        } else host.inContextOfModelMember(containerProperty.originDeclaration.kCallable) {
                            host.modelTypeRef(containerType.toKType())
                                .orFailWith { return@withTag it }
                        }

                        containerProperty.containerConfiguringFunction(host, kClass, containerTypeRef)
                    }.let { ExtractionResult.of(it, FunctionExtractionMetadata(listOf(containerProperty.originDeclaration))) }
                }
            }
        }
    )


    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        object : TypeDiscovery {
            override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<DiscoveredClass>> =
                containerProperties(typeDiscoveryServices.host, kClass).flatMap { property ->
                    listOfNotNull(
                        // discover the element type, only if the declared container type is not NDOC<T>; otherwise, it will be discovered from the signature
                        (property.containerType.takeIf { it.classifier != NamedDomainObjectContainer::class })
                            ?.let { DiscoveredClass.classesOf(it, DiscoveryTag.ContainerElement(property.originDeclaration.kCallable)).map(::schemaResult) }
                    ).flatten()
                }
        }
    )

    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        object : RuntimeCustomAccessors {
            override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
                val callable = containerByAccessorId[accessor.accessorIdentifier]?.originDeclaration?.kCallable ?: return InstanceAndPublicType.NULL
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

    private fun newElementFactoryFunction(host: SchemaBuildingHost, receiverTypeRef: DataTypeRef, elementKType: SupportedTypeProjection.SupportedType): SchemaResult<SchemaMemberFunction> {
        val elementFactoryFunction = elementFactoryFunction(host, receiverTypeRef, elementKType)
        if (elementFactoryFunction is SchemaResult.Result) {
            elementFactoryFunctions.add(elementFactoryFunction.result)
            elementPublicTypes[elementFactoryFunction.result] = elementKType.classifier.starProjectedType.jvmErasure
        }
        return elementFactoryFunction
    }

    private inner class ContainerProperty(
        val ownerType: KClass<*>,
        val name: String,
        val containerType: SupportedTypeProjection.SupportedType,
        val elementType: SupportedTypeProjection.SupportedType,
        val originDeclaration: SupportedCallable
    ) {
        fun syntheticTypeName(host: SchemaBuildingHost) =
            DefaultFqName.parse(NamedDomainObjectContainer::class.qualifiedName!! + "\$of\$" + elementTypeName(host).replace(".", "_"))

        private fun elementTypeName(host: SchemaBuildingHost) = dataTypeRefName(host, elementType.classifier as KClass<*>)

        private fun syntheticContainerTypeRef(host: SchemaBuildingHost) = DataTypeRefInternal.DefaultName(syntheticTypeName(host))

        fun containerConfiguringFunction(host: SchemaBuildingHost, ownerType: KClass<*>, containerTypeRef: DataTypeRef): SchemaResult<DataMemberFunction> = DefaultDataMemberFunction(
            host.containerTypeRef(ownerType)
                .orFailWith { return it },
            name,
            emptyList(),
            false,
            FunctionSemanticsInternal.DefaultAccessAndConfigure(
                ConfigureAccessorInternal.DefaultContainer(containerTypeRef, accessorId(host)),
                DefaultUnit,
                containerTypeRef,
                DefaultRequired
            )
        ).let(::schemaResult)

        fun accessorId(host: SchemaBuildingHost) = DefaultContainerAccessorIdentifier(name, dataTypeRefName(host, ownerType))

        fun generateSyntheticContainerType(host: SchemaBuildingHost): SchemaResult<DataClass> = DefaultDataClass(
            syntheticTypeName(host),
            NamedDomainObjectContainer::class.java.name,
            listOfNotNull((elementType.classifier as? KClass<*>)?.java?.name),
            emptySet(),
            emptyList(),
            listOf(newElementFactoryFunction(host, syntheticContainerTypeRef(host), elementType).orFailWith { return it }),
            emptyList()
        ).let(::schemaResult)
    }

    private fun containerProperties(host: SchemaBuildingHost, kClass: KClass<*>): List<ContainerProperty> {
        val members = host.classMembers(kClass).declarativeMembers
            .filter { member -> member.kCallable.annotationsWithGetters.none { it is Internal } }

        val propertiesFromMemberProperties = members.mapNotNull {
            if (it.kind != MemberKind.READ_ONLY_PROPERTY) return@mapNotNull null

            val elementType = elementTypeFromNdocContainerType(host, it.returnType) ?: return@mapNotNull null
            ContainerProperty(kClass, it.name, it.returnType, elementType, it)
        }
        val propertiesFromMemberFunctions = members.mapNotNull {
            if (it.kind != MemberKind.FUNCTION || it.parameters.isNotEmpty()) return@mapNotNull null

            val elementType = elementTypeFromNdocContainerType(host, it.returnType) ?: return@mapNotNull null
            ContainerProperty(kClass, it.kCallable.propertyName(), it.returnType, elementType, it)
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
    elementType: SupportedTypeProjection.SupportedType
): SchemaResult<DataMemberFunction> {
    val elementTypeRef = host.withTag(SchemaBuildingTags.containerElementType(elementType)) {
        host.modelTypeRef(elementType.toKType())
    }.orFailWith { return it }

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
    ).let(::schemaResult)
}

@OptIn(LossySchemaBuildingOperation::class) // modelTypeRef of a KClass \star projection should not fail; avoid the hassle of wrapping it into a result here
private fun dataTypeRefName(
    host: SchemaBuildingHost,
    it: KClass<*>
) = (host.modelTypeRef(it.starProjectedType).orError() as DataTypeRef.Name).fqName.toString()

