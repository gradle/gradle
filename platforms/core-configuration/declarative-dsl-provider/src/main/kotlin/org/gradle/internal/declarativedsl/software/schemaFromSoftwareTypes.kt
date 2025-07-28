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

package org.gradle.internal.declarativedsl.software

import org.gradle.api.Project
import org.gradle.api.internal.plugins.HasBuildModel
import org.gradle.api.internal.plugins.TargetTypeInformation
import org.gradle.api.internal.plugins.TargetTypeInformation.BuildModelTargetTypeInformation
import org.gradle.api.internal.plugins.TargetTypeInformation.DefinitionTargetTypeInformation
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.SchemaItemMetadataInternal.SchemaMemberOriginInternal.DefaultSoftwareFeatureOrigin
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.FixedTypeDiscovery
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.evaluator.softwareTypes.SOFTWARE_TYPE_ACCESSOR_PREFIX
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import org.gradle.plugin.software.internal.SoftwareFeatureApplicator
import org.gradle.plugin.software.internal.SoftwareFeatureImplementation
import org.gradle.plugin.software.internal.SoftwareFeatureRegistry
import kotlin.collections.associate
import kotlin.collections.flatten
import kotlin.collections.groupBy
import kotlin.collections.map
import kotlin.jvm.kotlin
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.starProjectedType


/**
 * Support for software types that are used as configuration blocks in build definitions.
 *
 * When a software type is used, the plugin providing it is applied to the project.
 *
 * If [withDefaultsApplication] is false, conventions are not applied when process the settings
 * build script, but are captured and applied when a project build script references a given software type.
 */
internal
fun EvaluationSchemaBuilder.softwareFeaturesComponent(
    rootSchemaType: KClass<*>,
    softwareFeatureRegistry: SoftwareFeatureRegistry,
    withDefaultsApplication: Boolean
) {
    // Maps from the parent binding type to the software feature implementations that can bind to it
    val featureIndex = buildSoftwareFeatureInfo(softwareFeatureRegistry) { replaceProjectWithSchemaTopLevelType(it, rootSchemaType) }

    // Register analysis schema components for each software feature that can bind to a given type
    registerAnalysisSchemaComponent(SoftwareFeatureComponent(featureIndex))

    if (withDefaultsApplication) {
        ifConversionSupported(mapper = { it as? ProjectInternal }) {
            registerObjectConversionComponent { project ->
                SoftwareFeatureConversionComponent(featureIndex.allFeatures, project.services.get(SoftwareFeatureApplicator::class.java))
            }
        }
    }

}

internal
fun EvaluationSchemaBuilder.softwareFeaturesDefaultsComponent(
    schemaTypeToExtend: KClass<*>,
    softwareFeatureRegistry: SoftwareFeatureRegistry
) {
    val softwareFeatureInfo = buildSoftwareTypeInfo(softwareFeatureRegistry) { replaceProjectWithSchemaTopLevelType(it, schemaTypeToExtend) }
    registerAnalysisSchemaComponent(SoftwareFeatureComponent(softwareFeatureInfo))
}

private
class SoftwareFeatureComponent(
    private val featureSchemaBindingIndex: SoftwareFeatureSchemaBindingIndex,
) : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FixedTypeDiscovery(
            null,
            featureSchemaBindingIndex.run { (bindingToDefinition.values + bindingByModelType.values).flatten().map { it.definitionPublicType.kotlin } }
        )
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        softwareFeatureConfiguringFunctions(featureSchemaBindingIndex)
    )
}

private
class SoftwareFeatureConversionComponent(
    private val softwareFeatureImplementations: List<SoftwareFeatureInfo<*, *>>,
    private val softwareFeatureApplicator: SoftwareFeatureApplicator
) : ObjectConversionComponent {
    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        RuntimeModelTypeAccessors(softwareFeatureApplicator, softwareFeatureImplementations)
    )
}


private class SoftwareFeatureSchemaBindingIndex(
    val bindingToDefinition: Map<KClass<*>, List<SoftwareFeatureInfo<*, *>>>,
    val bindingByModelType: Map<KClass<*>, List<SoftwareFeatureInfo<*, *>>>
) {
    val allFeatures = (bindingByModelType.values + bindingToDefinition.values).flatten()
}

private
fun buildSoftwareFeatureInfo(
    softwareFeatureRegistry: SoftwareFeatureRegistry,
    mapPluginTypeToSchemaType: (KClass<*>) -> KClass<*>
): SoftwareFeatureSchemaBindingIndex {
    val featureImplementations = softwareFeatureRegistry.getSoftwareFeatureImplementations().values.groupBy { it.targetDefinitionType }

    val featuresBoundToDefinition = featureImplementations.entries.mapNotNull { (key, value) -> if (key is DefinitionTargetTypeInformation) key to value else null }.toMap()
    val featuresBoundToModel = featureImplementations.entries.mapNotNull { (key, value) -> if (key is BuildModelTargetTypeInformation<*>) key to value else null }.toMap()

    return SoftwareFeatureSchemaBindingIndex(
        featuresBoundToDefinition.mapKeys { mapPluginTypeToSchemaType(it.key.definitionType.kotlin) }
            .mapValues { (_, value) -> value.map { SoftwareFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX) } },
        featuresBoundToModel.mapKeys { mapPluginTypeToSchemaType(it.key.buildModelType.kotlin) }
            .mapValues { (_, value) -> value.map { SoftwareFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX) } }
    )
}


private
fun buildSoftwareTypeInfo(
    softwareFeatureRegistry: SoftwareFeatureRegistry,
    pluginTypeToSchemaClassMapping: (KClass<*>) -> KClass<*>
): SoftwareFeatureSchemaBindingIndex {
    val softwareTypeInfo = softwareFeatureRegistry.getSoftwareFeatureImplementations().values.mapNotNull {
        it.targetDefinitionType.run {
            if (this is DefinitionTargetTypeInformation && definitionType == Project::class.java)
                SoftwareFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX)
            else null
        }
    }
    return SoftwareFeatureSchemaBindingIndex(
        softwareTypeInfo.groupBy { pluginTypeToSchemaClassMapping((it.targetDefinitionType as DefinitionTargetTypeInformation).definitionType.kotlin) },
        emptyMap()
    )
}


private
fun replaceProjectWithSchemaTopLevelType(bindingType: KClass<*>, rootSchemaType: KClass<*>): KClass<*> {
    return when (bindingType) {
        Project::class -> rootSchemaType
        else -> bindingType
    }
}


private
data class SoftwareFeatureInfo<T : Any, V : Any>(
    val delegate: SoftwareFeatureImplementation<T, V>,
    val accessorIdPrefix: String
) : SoftwareFeatureImplementation<T, V> by delegate {
    val customAccessorId = "$accessorIdPrefix:${delegate.featureName}"

    fun schemaFunction(host: SchemaBuildingHost, schemaTypeToExtend: KClass<*>) = host.withTag(softwareConfiguringFunctionTag(delegate.featureName)) {
        val receiverTypeRef = host.containerTypeRef(schemaTypeToExtend)
        DefaultDataMemberFunction(
            receiverTypeRef,
            delegate.featureName,
            emptyList(),
            isDirectAccessOnly = true,
            semantics = FunctionSemanticsInternal.DefaultAccessAndConfigure(
                accessor = ConfigureAccessorInternal.DefaultCustom(host.containerTypeRef(definitionPublicType.kotlin), customAccessorId),
                FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit,
                FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
            ),
            metadata = listOf(DefaultSoftwareFeatureOrigin(
                delegate.featureName,
                delegate.pluginClass.name,
                delegate.registeringPluginClass.name,
                delegate.registeringPluginId,
                (delegate.targetDefinitionType as? DefinitionTargetTypeInformation)?.definitionType?.name,
                (delegate.targetDefinitionType as? BuildModelTargetTypeInformation<*>)?.buildModelType?.name,
            ))
        )
    }

    private fun softwareConfiguringFunctionTag(name: String) = SchemaBuildingContextElement.TagContextElement("configuring function for '$name' software feature")
}


private
fun softwareFeatureConfiguringFunctions(softwareFeatureImplementations: SoftwareFeatureSchemaBindingIndex): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val classWithSupertypes = listOf(kClass.starProjectedType) + kClass.allSupertypes

        val featureImplementations = buildSet {
            classWithSupertypes.forEach { supertype ->
                softwareFeatureImplementations.bindingToDefinition[supertype.classifier]?.let(::addAll)
                if (supertype.classifier == HasBuildModel::class) {
                    val buildModelWithSupertypes = supertype.arguments.single().type.let { listOf(it) + (it?.classifier as? KClass<*>)?.allSupertypes.orEmpty() }
                    buildModelWithSupertypes.forEach { buildModelSupertype ->
                        softwareFeatureImplementations.bindingByModelType[buildModelSupertype?.classifier]?.let(::addAll)
                    }
                }
            }
        }

        return featureImplementations.map { it.schemaFunction(host, kClass) }
    }

    override fun constructors(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null
}


private class RuntimeModelTypeAccessors(
    private val softwareFeatureApplicator: SoftwareFeatureApplicator,
    info: List<SoftwareFeatureInfo<*, *>>
) : RuntimeCustomAccessors {

    val modelTypeById = info.associate { it.customAccessorId to it.delegate }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
        val softwareFeature = modelTypeById[accessor.customAccessorIdentifier]
            ?: return InstanceAndPublicType.NULL
        return InstanceAndPublicType.of(applySoftwareFeaturePlugin(receiverObject, softwareFeature, softwareFeatureApplicator), softwareFeature.definitionPublicType.kotlin)
    }

    private fun applySoftwareFeaturePlugin(receiverObject: Any, softwareFeature: SoftwareFeatureImplementation<*, *>, softwareFeatureApplicator: SoftwareFeatureApplicator): Any {
        require(receiverObject is ExtensionAware) { "unexpected receiver, expected a ExtensionAware instance, got $receiverObject" }
        require(isValidBindingType(softwareFeature.targetDefinitionType, receiverObject::class.java)) {
            "unexpected receiver; software feature ${softwareFeature.featureName} binds to '${softwareFeature.targetDefinitionType}', got '$receiverObject' definition"
        }
        return softwareFeatureApplicator.applyFeatureTo(receiverObject, softwareFeature)
    }

    private fun isValidBindingType(expectedTargetDefinitionType: TargetTypeInformation<*>, actualTargetDefinitionType: Class<*>): Boolean =
        when (expectedTargetDefinitionType) {
            is DefinitionTargetTypeInformation ->
                expectedTargetDefinitionType.definitionType.isAssignableFrom(actualTargetDefinitionType)

            is BuildModelTargetTypeInformation<*> ->
                HasBuildModel::class.java.isAssignableFrom(actualTargetDefinitionType) &&
                    actualTargetDefinitionType.kotlin.allSupertypes.find { it.classifier == HasBuildModel::class }
                        ?.let { (it.arguments.singleOrNull()?.type?.classifier as? KClass<*>)?.java?.let(expectedTargetDefinitionType.buildModelType::isAssignableFrom) }
                    ?: false

            else -> true
        }
}
