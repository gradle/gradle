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

import org.gradle.internal.declarativedsl.analysis.ConfigureAccessor
import org.gradle.internal.declarativedsl.analysis.DataConstructor
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.SchemaMemberFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import kotlin.reflect.KClass
import kotlin.reflect.KFunction


internal
class ProjectExtensionComponents(
    val typeDiscovery: TypeDiscovery,
    val functionExtractor: FunctionExtractor,
    val runtimeCustomAccessors: RuntimeCustomAccessors
)


internal
fun projectExtensionComponents(target: ProjectInternal, includeExtension: (KClass<*>) -> Boolean): ProjectExtensionComponents {
    val projectExtensions = projectExtensions(target, includeExtension)

    val typeDiscovery = FixedTypeDiscovery(ProjectTopLevelReceiver::class, projectExtensions.map { it.type })
    val functions = projectExtensionConfiguringFunctions(projectExtensions)
    val runtimeCustomAccessors = RuntimeProjectExtensionAccessors(target, projectExtensions)

    return ProjectExtensionComponents(typeDiscovery, functions, runtimeCustomAccessors)
}


private
fun projectExtensions(target: ProjectInternal, includeExtension: (KClass<*>) -> Boolean) =
    target.extensions.extensionsSchema.elements.mapNotNull {
        val type = it.publicType.concreteClass.kotlin
        if (includeExtension(type)) ProjectExtensionInfo(it.name, type) else null
    }


private
data class ProjectExtensionInfo(
    val name: String,
    val type: KClass<*>,
) {
    val customAccessorId = "projectExtension:$name"

    val schemaFunction = DataMemberFunction(
        ProjectTopLevelReceiver::class.toDataTypeRef(),
        name,
        emptyList(),
        isDirectAccessOnly = true,
        semantics = FunctionSemantics.AccessAndConfigure(
            accessor = ConfigureAccessor.Custom(type.toDataTypeRef(), customAccessorId),
            FunctionSemantics.AccessAndConfigure.ReturnType.UNIT
        )
    )
}


private
class RuntimeProjectExtensionAccessors(project: Project, info: List<ProjectExtensionInfo>) : RuntimeCustomAccessors {

    val extensionsByIdentifier = info.associate { it.customAccessorId to project.extensions.getByName(it.name) }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): Any? =
        if (receiverObject is Project)
            extensionsByIdentifier[accessor.customAccessorIdentifier]
        else null
}


private
fun projectExtensionConfiguringFunctions(projectExtensionInfo: Iterable<ProjectExtensionInfo>): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == ProjectTopLevelReceiver::class) projectExtensionInfo.map(ProjectExtensionInfo::schemaFunction) else emptyList()

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()

    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex) = null
}


private
class FixedTypeDiscovery(private val keyClass: KClass<*>, private val discoverClasses: List<KClass<*>>) : TypeDiscovery {
    override fun getClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> =
        when (kClass) {
            keyClass -> discoverClasses
            else -> emptyList()
        }
}
