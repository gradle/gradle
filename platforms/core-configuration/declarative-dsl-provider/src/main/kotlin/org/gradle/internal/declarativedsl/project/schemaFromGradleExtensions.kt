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

package org.gradle.internal.declarativedsl.project

import org.gradle.api.plugins.ExtensionAware
import org.gradle.declarative.dsl.model.annotations.VisibleInDefinition
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultSettingsExtensionAccessorIdentifier
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.FixedTypeDiscovery
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractionMetadata
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.LossySchemaBuildingOperation
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Special
import org.gradle.internal.declarativedsl.schemaBuilder.orError
import org.gradle.internal.declarativedsl.schemaBuilder.orFailWith
import org.gradle.internal.declarativedsl.schemaBuilder.schemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import kotlin.reflect.KClass
import kotlin.reflect.KFunction


/**
 * Introduces schema representations of Gradle extensions registered on an [ExtensionAware] object.
 *
 * Inspects a given [extensionContainer] extension owner and checks for its extensions which have types annotated with [org.gradle.declarative.dsl.model.annotations.VisibleInDefinition] (maybe in supertypes).
 *
 * Given that, introduces the following features in the schema:
 * * Type discovery ensuring that the types of the extensions get discovered and included in the schema (but just those types, not recursing)
 * * Function extractors which introduce configuring functions for the extensions
 *
 * If object conversion is enabled ([ifConversionSupported]):
 * * Runtime custom accessors as the runtime counterpart for the configuring functions, telling the runtime how to access the extensions.
 */
internal
fun EvaluationSchemaBuilder.thirdPartyExtensions(schemaTypeToExtend: KClass<*>, extensionContainer: ExtensionAware) {
    val extensions = getExtensionInfo(extensionContainer)
    registerAnalysisSchemaComponent(ThirdPartyExtensionsComponent(schemaTypeToExtend, extensions))
    ifConversionSupported {
        registerObjectConversionComponent(ThirdPartyExtensionsConversionComponent(extensions))
    }
}


private
class ThirdPartyExtensionsComponent(
    private val schemaTypeToExtend: KClass<*>,
    private val extensions: List<ExtensionInfo>,
) : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FixedTypeDiscovery(schemaTypeToExtend, extensions.map { TypeDiscovery.DiscoveredClass(it.type, listOf(Special("extension type"))) })
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        extensionConfiguringFunctions(schemaTypeToExtend, extensions)
    )
}


private
class ThirdPartyExtensionsConversionComponent(private val extensions: List<ExtensionInfo>) : ObjectConversionComponent {
    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> =
        listOf(RuntimeExtensionAccessors(extensions))
}


private
fun getExtensionInfo(target: ExtensionAware): List<ExtensionInfo> {
    val annotationChecker = CachedHierarchyAnnotationChecker(VisibleInDefinition::class)
    return target.extensions.extensionsSchema.elements.mapNotNull {
        val type = it.publicType.concreteClass.kotlin
        if (annotationChecker.isAnnotatedMaybeInSupertypes(type))
            ExtensionInfo(it.name, type) { target.extensions.getByName(it.name) }
        else null
    }
}


private
data class ExtensionInfo(
    val name: String,
    val type: KClass<*>,
    val extensionProvider: () -> Any,
) {
    val customAccessorId = DefaultSettingsExtensionAccessorIdentifier(name)

    fun schemaFunction(host: SchemaBuildingHost): SchemaResult<DataMemberFunction> {
        return host.withTag(extensionTag(name)) {
            val objectType = host.containerTypeRef(type)
                .orFailWith { return it }

            DefaultDataMemberFunction(
                @OptIn(LossySchemaBuildingOperation::class) // referencing a predefined type is safe
                host.containerTypeRef(ProjectTopLevelReceiver::class).orError(),
                name,
                emptyList(),
                isDirectAccessOnly = true,
                semantics = FunctionSemanticsInternal.DefaultAccessAndConfigure(
                    accessor = ConfigureAccessorInternal.DefaultExtension(objectType, customAccessorId),
                    FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit,
                    objectType,
                    FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
                )
            ).let(::schemaResult)
        }
    }

    private fun extensionTag(name: String) = SchemaBuildingContextElement.TagContextElement("'$name' extension")
}


private
class RuntimeExtensionAccessors(info: List<ExtensionInfo>) : RuntimeCustomAccessors {

    val providersByIdentifier = info.associate { it.customAccessorId to it.extensionProvider() }
    val typesByIdentifier = info.associate { it.customAccessorId to it.type }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType =
        InstanceAndPublicType.of(providersByIdentifier[accessor.accessorIdentifier], typesByIdentifier[accessor.accessorIdentifier])
}


private
fun extensionConfiguringFunctions(typeToExtend: KClass<*>, extensionInfo: Iterable<ExtensionInfo>): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): List<FunctionExtractionResult> =
        if (kClass == typeToExtend) extensionInfo.map { ExtractionResult.of(it.schemaFunction(host), FunctionExtractionMetadata(emptyList())) } else emptyList()

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): SchemaResult<DataTopLevelFunction>? = null
}


private
class CachedHierarchyAnnotationChecker(private val annotationType: KClass<out Annotation>) {
    fun isAnnotatedMaybeInSupertypes(kClass: KClass<*>): Boolean {
        // Can't use computeIfAbsent because of recursive calls
        hasAnnotationWithSuperTypesCache[kClass]?.let { return it }
        return hasRestrictedAnnotationWithSuperTypes(kClass).also { hasRestrictedAnnotationCache[kClass] = it }
    }

    private
    fun hasRestrictedAnnotationWithSuperTypes(kClass: KClass<*>) =
        hasRestrictedAnnotationCached(kClass) || kClass.supertypes.any { (it.classifier as? KClass<*>)?.let { isAnnotatedMaybeInSupertypes(it) } ?: false }

    private
    val hasAnnotationWithSuperTypesCache = mutableMapOf<KClass<*>, Boolean>()

    private
    val hasRestrictedAnnotationCache = mutableMapOf<KClass<*>, Boolean>()

    private
    fun hasRestrictedAnnotationCached(kClass: KClass<*>) = hasRestrictedAnnotationCache.computeIfAbsent(kClass) { hasAnnotation(it) }

    private
    fun hasAnnotation(kClass: KClass<*>) = kClass.annotations.any { annotationType.isInstance(it) }
}
