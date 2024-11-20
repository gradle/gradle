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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.FixedTypeDiscovery
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.evaluator.softwareTypes.SOFTWARE_TYPE_ACCESSOR_PREFIX
import org.gradle.internal.declarativedsl.mappingToJvm.InstanceAndPublicType
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.nullInstanceAndPublicType
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import org.gradle.plugin.software.internal.SoftwareFeatureApplicator
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
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
fun EvaluationSchemaBuilder.softwareTypesComponent(
    schemaTypeToExtend: KClass<*>,
    softwareTypeRegistry: SoftwareTypeRegistry,
    withDefaultsApplication: Boolean
) {
    val softwareTypeInfo = buildSoftwareTypeInfo(softwareTypeRegistry, schemaTypeToExtend)
    registerAnalysisSchemaComponent(SoftwareTypeComponent(schemaTypeToExtend, softwareTypeInfo))

    if (withDefaultsApplication) {
        ifConversionSupported(mapper = { it as? ProjectInternal }) {
            registerObjectConversionComponent { project ->
                SoftwareTypeConversionComponent(softwareTypeInfo, project.services.get(SoftwareFeatureApplicator::class.java))
            }
        }
    }

}

internal
fun EvaluationSchemaBuilder.softwareTypesDefaultsComponent(
    schemaTypeToExtend: KClass<*>,
    softwareTypeRegistry: SoftwareTypeRegistry
) {
    val softwareTypeInfo = buildSoftwareTypeInfoWithoutResolution(softwareTypeRegistry, schemaTypeToExtend)
    registerAnalysisSchemaComponent(SoftwareTypeComponent(schemaTypeToExtend, softwareTypeInfo))
}


private
class SoftwareTypeComponent(
    private val schemaTypeToExtend: KClass<*>,
    private val softwareTypeImplementations: List<SoftwareTypeInfo<*>>
) : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FixedTypeDiscovery(schemaTypeToExtend, softwareTypeImplementations.map { it.modelPublicType.kotlin })
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        softwareTypeConfiguringFunctions(schemaTypeToExtend, softwareTypeImplementations)
    )
}


private
class SoftwareTypeConversionComponent(
    private val softwareTypeImplementations: List<SoftwareTypeInfo<*>>,
    private val softwareFeatureApplicator: SoftwareFeatureApplicator
) : ObjectConversionComponent {
    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        RuntimeModelTypeAccessors(softwareFeatureApplicator, softwareTypeImplementations)
    )
}

private
fun buildSoftwareTypeInfo(
    softwareTypeRegistry: SoftwareTypeRegistry,
    schemaTypeToExtend: KClass<*>
): List<SoftwareTypeInfo<out Any?>> = softwareTypeRegistry.getSoftwareTypeImplementations().values.map {
    SoftwareTypeInfo(it, schemaTypeToExtend, SOFTWARE_TYPE_ACCESSOR_PREFIX)
}

private
fun buildSoftwareTypeInfoWithoutResolution(
    softwareTypeRegistry: SoftwareTypeRegistry,
    schemaTypeToExtend: KClass<*>
): List<SoftwareTypeInfo<out Any?>> = softwareTypeRegistry.getSoftwareTypeImplementations().values.map {
    SoftwareTypeInfo(it, schemaTypeToExtend, SOFTWARE_TYPE_ACCESSOR_PREFIX)
}


private
data class SoftwareTypeInfo<T>(
    val delegate: SoftwareTypeImplementation<T>,
    val schemaTypeToExtend: KClass<*>,
    val accessorIdPrefix: String,
) : SoftwareTypeImplementation<T> by delegate {
    val customAccessorId = "$accessorIdPrefix:${delegate.softwareType}"

    val schemaFunction = DefaultDataMemberFunction(
        schemaTypeToExtend.toDataTypeRef(),
        delegate.softwareType,
        emptyList(),
        isDirectAccessOnly = true,
        semantics = FunctionSemanticsInternal.DefaultAccessAndConfigure(
            accessor = ConfigureAccessorInternal.DefaultCustom(delegate.modelPublicType.kotlin.toDataTypeRef(), customAccessorId),
            FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit,
            FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
        )
    )
}


private
fun softwareTypeConfiguringFunctions(typeToExtend: KClass<*>, softwareTypeImplementations: Iterable<SoftwareTypeInfo<*>>): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == typeToExtend) softwareTypeImplementations.map(SoftwareTypeInfo<*>::schemaFunction) else emptyList()

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()

    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex) = null
}


private
class RuntimeModelTypeAccessors(
    private val softwareFeatureApplicator: SoftwareFeatureApplicator,
    info: List<SoftwareTypeInfo<*>>
) : RuntimeCustomAccessors {

    val modelTypeById = info.associate { it.customAccessorId to it.delegate }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
        val softwareType = modelTypeById[accessor.customAccessorIdentifier]
            ?: return nullInstanceAndPublicType
        return applySoftwareTypePlugin(receiverObject, softwareType, softwareFeatureApplicator) to softwareType.modelPublicType.kotlin
    }

    private
    fun applySoftwareTypePlugin(receiverObject: Any, softwareType: SoftwareTypeImplementation<*>, softwareFeatureApplicator: SoftwareFeatureApplicator): Any {
        require(receiverObject is ProjectInternal) { "unexpected receiver, expected a ProjectInternal instance, got $receiverObject" }
        return softwareFeatureApplicator.applyFeatureTo(receiverObject, softwareType)
    }
}
