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
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import kotlin.reflect.KClass
import kotlin.reflect.KFunction


/**
 * Introduces schema representations of Gradle extensions registered on a [Project].
 *
 * Inspects a given [target] project and checks for its extensions which have types annotated with [Restricted] (maybe in supertypes).
 *
 * Given that, introduces the following features in the schema:
 * * [typeDiscovery] ensuring that the types of the project extensions get discovered and included in the schema (but just those types, not recursing)
 * * [functionExtractors] which introduce configuring functions for the project extensions
 * * [runtimeCustomAccessors] as the runtime counterpart for the configuring functions, telling the runtime how to access the extensions.
 */
internal
class ThirdPartyProjectExtensionsComponent(
    private val target: ProjectInternal,
) : EvaluationSchemaComponent {
    private
    val projectExtensions: List<ProjectExtensionInfo> = run {
        val annotationChecker = CachedHierarchyAnnotationChecker(Restricted::class)
        projectExtensions(target) { annotationChecker.isAnnotatedMaybeInSupertypes(it) }
    }

    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        FixedTypeDiscovery(ProjectTopLevelReceiver::class, projectExtensions.map { it.type })
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        projectExtensionConfiguringFunctions(projectExtensions)
    )

    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf(
        RuntimeProjectExtensionAccessors(target, projectExtensions)
    )
}


private
fun projectExtensions(target: ProjectInternal, includeExtension: (KClass<*>) -> Boolean): List<ProjectExtensionInfo> =
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
class CachedHierarchyAnnotationChecker(private val annotationType: KClass<out Annotation>) {
    fun isAnnotatedMaybeInSupertypes(kClass: KClass<*>): Boolean {
        // Can't use computeIfAbsent because of recursive calls
        hasRestrictedAnnotationWithSuperTypesCache[kClass]?.let { return it }
        return hasRestrictedAnnotationWithSuperTypes(kClass).also { hasRestrictedAnnotationCache[kClass] = it }
    }

    private
    fun hasRestrictedAnnotationWithSuperTypes(kClass: KClass<*>) =
        hasRestrictedAnnotationCached(kClass) || kClass.supertypes.any { (it.classifier as? KClass<*>)?.let { isAnnotatedMaybeInSupertypes(it) } ?: false }

    private
    val hasRestrictedAnnotationWithSuperTypesCache = mutableMapOf<KClass<*>, Boolean>()

    private
    val hasRestrictedAnnotationCache = mutableMapOf<KClass<*>, Boolean>()

    private
    fun hasRestrictedAnnotationCached(kClass: KClass<*>) = hasRestrictedAnnotationCache.computeIfAbsent(kClass) { hasAnnotation(it) }

    private
    fun hasAnnotation(kClass: KClass<*>) = kClass.annotations.any { annotationType.isInstance(it) }
}
