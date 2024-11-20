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
import org.gradle.declarative.dsl.model.annotations.Restricted
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
import org.gradle.internal.declarativedsl.mappingToJvm.InstanceAndPublicType
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import kotlin.reflect.KClass
import kotlin.reflect.KFunction


/**
 * Introduces schema representations of Gradle extensions registered on an [ExtensionAware] object.
 *
 * Inspects a given [extensionContainer] extension owner and checks for its extensions which have types annotated with [Restricted] (maybe in supertypes).
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
        FixedTypeDiscovery(schemaTypeToExtend, extensions.map { it.type })
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        extensionConfiguringFunctions(schemaTypeToExtend, extensions)
    )
}


private
const val SETTINGS_EXTENSION_ACCESSOR_PREFIX = "settingsExtension"


private
class ThirdPartyExtensionsConversionComponent(private val extensions: List<ExtensionInfo>) : ObjectConversionComponent {
    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> =
        listOf(RuntimeExtensionAccessors(extensions))
}


private
fun getExtensionInfo(target: ExtensionAware): List<ExtensionInfo> {
    val annotationChecker = CachedHierarchyAnnotationChecker(Restricted::class)
    return target.extensions.extensionsSchema.elements.mapNotNull {
        val type = it.publicType.concreteClass.kotlin
        if (annotationChecker.isAnnotatedMaybeInSupertypes(type))
            ExtensionInfo(it.name, type, SETTINGS_EXTENSION_ACCESSOR_PREFIX) { target.extensions.getByName(it.name) }
        else null
    }
}


private
data class ExtensionInfo(
    val name: String,
    val type: KClass<*>,
    val accessorIdPrefix: String,
    val extensionProvider: () -> Any,
) {
    val customAccessorId = "$accessorIdPrefix:$name"

    val schemaFunction = DefaultDataMemberFunction(
        ProjectTopLevelReceiver::class.toDataTypeRef(),
        name,
        emptyList(),
        isDirectAccessOnly = true,
        semantics = FunctionSemanticsInternal.DefaultAccessAndConfigure(
            accessor = ConfigureAccessorInternal.DefaultCustom(type.toDataTypeRef(), customAccessorId),
            FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit,
            FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
        )
    )
}


private
class RuntimeExtensionAccessors(info: List<ExtensionInfo>) : RuntimeCustomAccessors {

    val providersByIdentifier = info.associate { it.customAccessorId to it.extensionProvider() }
    val typesByIdentifier = info.associate { it.customAccessorId to it.type }

    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType =
        providersByIdentifier[accessor.customAccessorIdentifier] to typesByIdentifier[accessor.customAccessorIdentifier]
}


private
fun extensionConfiguringFunctions(typeToExtend: KClass<*>, extensionInfo: Iterable<ExtensionInfo>): FunctionExtractor = object : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == typeToExtend) extensionInfo.map(ExtensionInfo::schemaFunction) else emptyList()

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
