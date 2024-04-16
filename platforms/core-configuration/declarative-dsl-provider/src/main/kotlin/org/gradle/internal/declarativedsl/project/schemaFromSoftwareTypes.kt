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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import java.util.function.Supplier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction


internal
class SoftwareTypeComponent(
    private val schemaTypeToExtend: KClass<*>,
    private val target: ProjectInternal,
    private val accessorIdPrefix: String,
) : EvaluationSchemaComponent {
    private
    val softwareTypeRegistry = target.services.get(SoftwareTypeRegistry::class.java)
    private
    val softwareTypeImplementations = softwareTypeRegistry.getSoftwareTypeImplementations().map {
        SoftwareTypeInfo(it, accessorIdPrefix) {
            target.pluginManager.apply(it.pluginClass)
            target.extensions.getByName(it.softwareType)
        }
    }

    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FixedTypeDiscovery(schemaTypeToExtend, softwareTypeImplementations.map { it.modelPublicType.kotlin })
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        extensionConfiguringFunctions(schemaTypeToExtend, softwareTypeImplementations)
    )

    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        RuntimeModelTypeAccessors(softwareTypeImplementations)
    )
}


private
data class SoftwareTypeInfo(
    val delegate: SoftwareTypeImplementation,
    val accessorIdPrefix: String,
    val extensionProvider: Supplier<Any>
) : SoftwareTypeImplementation by delegate {
    val customAccessorId = "$accessorIdPrefix:${delegate.softwareType}"

    val schemaFunction = DataMemberFunction(
        ProjectTopLevelReceiver::class.toDataTypeRef(),
        delegate.softwareType,
        emptyList(),
        isDirectAccessOnly = true,
        semantics = FunctionSemanticsInternal.AccessAndConfigure(
            accessor = ConfigureAccessorInternal.Custom(delegate.modelPublicType.kotlin.toDataTypeRef(), customAccessorId),
            FunctionSemanticsInternal.AccessAndConfigure.ReturnType.UNIT
        )
    )
}


private
fun extensionConfiguringFunctions(typeToExtend: KClass<*>, softwareTypeImplementations: Iterable<SoftwareTypeInfo>): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == typeToExtend) softwareTypeImplementations.map(SoftwareTypeInfo::schemaFunction) else emptyList()

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()

    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex) = null
}


private
class RuntimeModelTypeAccessors(info: List<SoftwareTypeInfo>) : RuntimeCustomAccessors {

    val modelTypeById = info.associate { it.customAccessorId to it.extensionProvider }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessorInternal.Custom): Any? =
        modelTypeById[accessor.customAccessorIdentifier]?.get()
}
