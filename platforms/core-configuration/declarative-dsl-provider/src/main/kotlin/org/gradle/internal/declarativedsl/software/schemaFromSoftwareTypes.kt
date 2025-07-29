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
import kotlin.reflect.KClass
import kotlin.reflect.KFunction


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
    val softwareFeatureInfos: Map<KClass<*>, List<SoftwareFeatureInfo<*>>> = buildSoftwareFeatureInfo(softwareFeatureRegistry)
    softwareFeatureInfos.forEach { (bindingType, softwareFeatureInfoList) ->
        registerAnalysisSchemaComponent(SoftwareFeatureComponent(mapToSchemaType(bindingType, rootSchemaType), softwareFeatureInfoList))
    }

    if (withDefaultsApplication) {
        ifConversionSupported(mapper = { it as? ProjectInternal }) {
            registerObjectConversionComponent { project ->
                SoftwareFeatureConversionComponent(softwareFeatureInfos.values.flatten(), project.services.get(SoftwareFeatureApplicator::class.java))
            }
        }
    }

}

internal
fun EvaluationSchemaBuilder.softwareFeaturesDefaultsComponent(
    schemaTypeToExtend: KClass<*>,
    softwareFeatureRegistry: SoftwareFeatureRegistry
) {
    val softwareFeatureInfo = buildSoftwareTypeInfoWithoutResolution(softwareFeatureRegistry)
    registerAnalysisSchemaComponent(SoftwareFeatureComponent(schemaTypeToExtend, softwareFeatureInfo))
}


private
class SoftwareFeatureComponent(
    private val schemaTypeToExtend: KClass<*>,
    private val softwareFeatureImplementations: List<SoftwareFeatureInfo<*>>
) : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FixedTypeDiscovery(schemaTypeToExtend, softwareFeatureImplementations.map { it.definitionPublicType.kotlin })
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        softwareFeatureConfiguringFunctions(schemaTypeToExtend, softwareFeatureImplementations)
    )
}


private
class SoftwareFeatureConversionComponent(
    private val softwareFeatureImplementations: List<SoftwareFeatureInfo<*>>,
    private val softwareFeatureApplicator: SoftwareFeatureApplicator
) : ObjectConversionComponent {
    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        RuntimeModelTypeAccessors(softwareFeatureApplicator, softwareFeatureImplementations)
    )
}


private
fun buildSoftwareFeatureInfo(
    softwareFeatureRegistry: SoftwareFeatureRegistry
): Map<KClass<*>, List<SoftwareFeatureInfo<*>>> = softwareFeatureRegistry.getSoftwareFeatureImplementations().values
        .map { SoftwareFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX) }
        .groupBy { it.bindingType.kotlin }


private
fun buildSoftwareTypeInfoWithoutResolution(
    softwareFeatureRegistry: SoftwareFeatureRegistry
): List<SoftwareFeatureInfo<*>> = softwareFeatureRegistry.getSoftwareFeatureImplementations().values.map {
    SoftwareFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX)
}


private
fun mapToSchemaType(bindingType: KClass<*>, rootSchemaType: KClass<*>) : KClass<*>  {
    return when (bindingType) {
        Project::class -> rootSchemaType
        else -> bindingType
    }
}


private
data class SoftwareFeatureInfo<T : Any>(
    val delegate: SoftwareFeatureImplementation<T>,
    val accessorIdPrefix: String,
) : SoftwareFeatureImplementation<T> by delegate {
    val schemaTypeToExtend: KClass<*> = delegate.bindingType.kotlin
    val customAccessorId = "$accessorIdPrefix:${delegate.featureName}"

    fun schemaFunction(host: SchemaBuildingHost) = host.withTag(softwareConfiguringFunctionTag(delegate.featureName)) {
        DefaultDataMemberFunction(
            host.containerTypeRef(schemaTypeToExtend),
            delegate.featureName,
            emptyList(),
            isDirectAccessOnly = true,
            semantics = FunctionSemanticsInternal.DefaultAccessAndConfigure(
                accessor = ConfigureAccessorInternal.DefaultCustom(host.containerTypeRef(definitionPublicType.kotlin), customAccessorId),
                FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit,
                FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
            )
        )
    }

    private fun softwareConfiguringFunctionTag(name: String) = SchemaBuildingContextElement.TagContextElement("configuring function for '$name' software type")
}


private
fun softwareFeatureConfiguringFunctions(typeToExtend: KClass<*>, softwareFeatureImplementations: Iterable<SoftwareFeatureInfo<*>>): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == typeToExtend) softwareFeatureImplementations.map { it.schemaFunction(host) } else emptyList()

    override fun constructors(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null
}


private
class RuntimeModelTypeAccessors(
    private val softwareFeatureApplicator: SoftwareFeatureApplicator,
    info: List<SoftwareFeatureInfo<*>>
) : RuntimeCustomAccessors {

    val modelTypeById = info.associate { it.customAccessorId to it.delegate }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
        val softwareFeature = modelTypeById[accessor.customAccessorIdentifier]
            ?: return InstanceAndPublicType.NULL
        return InstanceAndPublicType.of(applySoftwareFeaturePlugin(receiverObject, softwareFeature, softwareFeatureApplicator), softwareFeature.definitionPublicType.kotlin)
    }

    private
    fun applySoftwareFeaturePlugin(receiverObject: Any, softwareFeature: SoftwareFeatureImplementation<*>, softwareFeatureApplicator: SoftwareFeatureApplicator): Any {
        require(receiverObject is ExtensionAware) { "unexpected receiver, expected a ExtensionAware instance, got $receiverObject" }
        require(softwareFeature.bindingType.isAssignableFrom(receiverObject::class.java)) { "unexpected receiver, expected a ${softwareFeature.bindingType}, got $receiverObject" }
        return softwareFeatureApplicator.applyFeatureTo(receiverObject, softwareFeature)
    }
}
