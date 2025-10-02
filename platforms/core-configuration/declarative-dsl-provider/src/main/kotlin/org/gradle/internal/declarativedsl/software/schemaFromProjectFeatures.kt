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
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.TargetTypeInformation.BuildModelTargetTypeInformation
import org.gradle.api.internal.plugins.TargetTypeInformation.DefinitionTargetTypeInformation
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.SchemaItemMetadataInternal.SchemaMemberOriginInternal.DefaultProjectFeatureOrigin
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.FixedTypeDiscovery
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.evaluator.projectTypes.SOFTWARE_TYPE_ACCESSOR_PREFIX
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import org.gradle.plugin.software.internal.ProjectFeatureApplicator
import org.gradle.plugin.software.internal.ProjectFeatureImplementation
import org.gradle.plugin.software.internal.ProjectFeatureRegistry
import org.gradle.plugin.software.internal.TargetTypeInformationChecks
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.starProjectedType


/**
 * Support for project features that are used as configuration blocks in build definitions.
 *
 * When a project feature is used, the plugin providing it is applied to the project.
 *
 * If [withDefaultsApplication] is false, conventions are not applied when process the settings
 * build script, but are captured and applied when a project build script references a given project feature.
 */
internal
fun EvaluationSchemaBuilder.projectFeaturesComponent(
    rootSchemaType: KClass<*>,
    projectFeatureRegistry: ProjectFeatureRegistry,
    withDefaultsApplication: Boolean
) {
    // Maps from the parent binding type to the project feature implementations that can bind to it
    val featureIndex = buildProjectFeatureInfo(projectFeatureRegistry) { replaceProjectWithSchemaTopLevelType(it, rootSchemaType) }

    // Register analysis schema components for each project feature that can bind to a given type
    registerAnalysisSchemaComponent(ProjectFeatureComponent(featureIndex))

    if (withDefaultsApplication) {
        ifConversionSupported(mapper = { it as? ProjectInternal }) {
            registerObjectConversionComponent { project ->
                ProjectFeatureConversionComponent(featureIndex.allFeatures, project.services.get(ProjectFeatureApplicator::class.java))
            }
        }
    }

}

internal
fun EvaluationSchemaBuilder.projectFeaturesDefaultsComponent(
    schemaTypeToExtend: KClass<*>,
    projectFeatureRegistry: ProjectFeatureRegistry
) {
    val projectFeatureInfo = buildProjectTypeInfo(projectFeatureRegistry) { replaceProjectWithSchemaTopLevelType(it, schemaTypeToExtend) }
    registerAnalysisSchemaComponent(ProjectFeatureComponent(projectFeatureInfo))
}

private
class ProjectFeatureComponent(
    private val featureSchemaBindingIndex: ProjectFeatureSchemaBindingIndex,
) : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FixedTypeDiscovery(
            null,
            featureSchemaBindingIndex.run { (bindingToDefinition.values + bindingByModelType.values).flatten().map { it.definitionPublicType.kotlin } }
        )
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        projectFeatureConfiguringFunctions(featureSchemaBindingIndex)
    )
}

private
class ProjectFeatureConversionComponent(
    private val projectFeatureImplementations: List<ProjectFeatureInfo<*, *>>,
    private val projectFeatureApplicator: ProjectFeatureApplicator
) : ObjectConversionComponent {
    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        RuntimeModelTypeAccessors(projectFeatureApplicator, projectFeatureImplementations)
    )
}


private class ProjectFeatureSchemaBindingIndex(
    val bindingToDefinition: Map<KClass<*>, List<ProjectFeatureInfo<*, *>>>,
    val bindingByModelType: Map<KClass<*>, List<ProjectFeatureInfo<*, *>>>
) {
    val allFeatures = (bindingByModelType.values + bindingToDefinition.values).flatten()
}

private
fun buildProjectFeatureInfo(
    projectFeatureRegistry: ProjectFeatureRegistry,
    mapPluginTypeToSchemaType: (KClass<*>) -> KClass<*>
): ProjectFeatureSchemaBindingIndex {
    val featureImplementations = projectFeatureRegistry.getProjectFeatureImplementations().values.groupBy { it.targetDefinitionType }

    val featuresBoundToDefinition = featureImplementations.entries.mapNotNull { (key, value) -> if (key is DefinitionTargetTypeInformation) key to value else null }.toMap()
    val featuresBoundToModel = featureImplementations.entries.mapNotNull { (key, value) -> if (key is BuildModelTargetTypeInformation<*>) key to value else null }.toMap()

    return ProjectFeatureSchemaBindingIndex(
        featuresBoundToDefinition.mapKeys { mapPluginTypeToSchemaType(it.key.definitionType.kotlin) }
            .mapValues { (_, value) -> value.map { ProjectFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX) } },
        featuresBoundToModel.mapKeys { mapPluginTypeToSchemaType(it.key.buildModelType.kotlin) }
            .mapValues { (_, value) -> value.map { ProjectFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX) } }
    )
}


private
fun buildProjectTypeInfo(
    projectFeatureRegistry: ProjectFeatureRegistry,
    pluginTypeToSchemaClassMapping: (KClass<*>) -> KClass<*>
): ProjectFeatureSchemaBindingIndex {
    val projectTypeInfo = projectFeatureRegistry.getProjectFeatureImplementations().values.mapNotNull {
        it.targetDefinitionType.run {
            if (this is DefinitionTargetTypeInformation && definitionType == Project::class.java)
                ProjectFeatureInfo(it, SOFTWARE_TYPE_ACCESSOR_PREFIX)
            else null
        }
    }
    return ProjectFeatureSchemaBindingIndex(
        projectTypeInfo.groupBy { pluginTypeToSchemaClassMapping((it.targetDefinitionType as DefinitionTargetTypeInformation).definitionType.kotlin) },
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
data class ProjectFeatureInfo<T : Any, V : Any>(
    val delegate: ProjectFeatureImplementation<T, V>,
    val accessorIdPrefix: String
) : ProjectFeatureImplementation<T, V> by delegate {
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
            metadata = listOf(DefaultProjectFeatureOrigin(
                delegate.featureName,
                delegate.pluginClass.name,
                delegate.registeringPluginClass.name,
                delegate.registeringPluginId,
                (delegate.targetDefinitionType as? DefinitionTargetTypeInformation)?.definitionType?.name,
                (delegate.targetDefinitionType as? BuildModelTargetTypeInformation<*>)?.buildModelType?.name,
            ))
        )
    }

    private fun softwareConfiguringFunctionTag(name: String) = SchemaBuildingContextElement.TagContextElement("configuring function for '$name' project feature")
}


private
fun projectFeatureConfiguringFunctions(projectFeatureImplementations: ProjectFeatureSchemaBindingIndex): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val classWithSupertypes = listOf(kClass.starProjectedType) + kClass.allSupertypes

        val featureImplementations = buildSet {
            classWithSupertypes.forEach { supertype ->
                projectFeatureImplementations.bindingToDefinition[supertype.classifier]?.let(::addAll)
                if (supertype.classifier == Definition::class) {
                    val buildModelWithSupertypes = supertype.arguments.single().type.let { listOf(it) + (it?.classifier as? KClass<*>)?.allSupertypes.orEmpty() }
                    buildModelWithSupertypes.forEach { buildModelSupertype ->
                        projectFeatureImplementations.bindingByModelType[buildModelSupertype?.classifier]?.let(::addAll)
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
    private val projectFeatureApplicator: ProjectFeatureApplicator,
    info: List<ProjectFeatureInfo<*, *>>
) : RuntimeCustomAccessors {

    val modelTypeById = info.associate { it.customAccessorId to it.delegate }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
        val projectFeature = modelTypeById[accessor.customAccessorIdentifier]
            ?: return InstanceAndPublicType.NULL
        return InstanceAndPublicType.of(applyProjectFeaturePlugin(receiverObject, projectFeature, projectFeatureApplicator), projectFeature.definitionPublicType.kotlin)
    }

    private fun applyProjectFeaturePlugin(receiverObject: Any, projectFeature: ProjectFeatureImplementation<*, *>, projectFeatureApplicator: ProjectFeatureApplicator): Any {
        require(receiverObject is DynamicObjectAware) { "unexpected receiver, expected a DynamicObjectAware instance, got $receiverObject" }
        require(TargetTypeInformationChecks.isValidBindingType(projectFeature.targetDefinitionType, receiverObject::class.java)) {
            "unexpected receiver; project feature ${projectFeature.featureName} binds to '${projectFeature.targetDefinitionType}', got '$receiverObject' definition"
        }
        return projectFeatureApplicator.applyFeatureTo(receiverObject, projectFeature)
    }
}
