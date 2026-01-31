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

import org.gradle.api.Project
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.FixedTypeDiscovery
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultPropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.MemberKind
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Special
import org.gradle.internal.declarativedsl.schemaBuilder.schemaResult
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf


/**
 * Brings the typesafe project accessors into the schema if the feature is enabled by doing the following:
 * * in the [ProjectTopLevelReceiver] type, introduces a `projects` property of type `RootProjectAccessor`;
 * * brings the `RootProjectAccessor` type into type discovery, and
 * * ensures type discovery going recursively over the generated typesafe accessor containers (see [TypesafeProjectAccessorTypeDiscovery]);
 * * for each typesafe accessor container type, extracts its properties (see [TypesafeProjectPropertyProducer]);
 * * at runtime, resolves property access to the `projects` property on [Project] instances.
 */
@Suppress("unused") // temporarily excluded from the schema, awaiting rework in a way that does not require the target scope
internal
class TypesafeProjectAccessorsComponent(targetScope: ClassLoaderScope) : ObjectConversionComponent, AnalysisSchemaComponent {
    private
    val projectAccessorsClass: KClass<*>? = try {
        targetScope.localClassLoader.loadClass("org.gradle.accessors.dm.RootProjectAccessor").kotlin
    } catch (_: ClassNotFoundException) {
        null
    }

    private
    val projectAccessorsExtension: DataProperty? = projectAccessorsClass?.let {
        DefaultDataProperty(
            "projects",
            valueType = DataTypeRefInternal.DefaultName(DefaultFqName.parse(projectAccessorsClass.qualifiedName!!)),
            mode = DefaultDataProperty.DefaultPropertyMode.DefaultReadOnly,
            hasDefaultValue = true,
            isHiddenInDsl = false,
            isDirectAccessOnly = false,
        )
    }

    override fun propertyExtractors(): List<PropertyExtractor> = when (projectAccessorsExtension) {
        null -> emptyList()
        else -> listOf(
            TypesafeProjectPropertyProducer(),
            ExtensionProperties(mapOf(ProjectTopLevelReceiver::class to listOf(projectAccessorsExtension)))
        )
    }

    override fun typeDiscovery(): List<TypeDiscovery> = when (projectAccessorsClass) {
        null -> emptyList()
        else -> listOf(
            FixedTypeDiscovery(ProjectTopLevelReceiver::class, listOf(TypeDiscovery.DiscoveredClass(projectAccessorsClass, listOf(Special("project accessors"))))),
            TypesafeProjectAccessorTypeDiscovery()
        )
    }

    override fun runtimePropertyResolvers(): List<RuntimePropertyResolver> = listOf(ProjectPropertyAccessorRuntimeResolver())
}


private
class ProjectPropertyAccessorRuntimeResolver : RuntimePropertyResolver {
    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.ReadResolution =
        if (receiverClass.isSubclassOf(Project::class) && name == "projects") {
            RuntimePropertyResolver.ReadResolution.ResolvedRead { receiver ->
                val value = (receiver as Project).extensions.getByName("projects")
                InstanceAndPublicType.of(value, value::class) }
        } else RuntimePropertyResolver.ReadResolution.UnresolvedRead

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String) = RuntimePropertyResolver.WriteResolution.UnresolvedWrite
}


private
class TypesafeProjectAccessorTypeDiscovery : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<TypeDiscovery.DiscoveredClass>> {
        return if (kClass.isGeneratedAccessors()) {
            allClassesReachableFromGetters(typeDiscoveryServices.host, kClass).map {
                schemaResult(TypeDiscovery.DiscoveredClass(it, listOf(Special("type-safe project accessor"))))
            }
        } else {
            emptyList()
        }
    }

    private
    fun allClassesReachableFromGetters(host: SchemaBuildingHost, kClass: KClass<*>) = buildSet {
        fun visit(kClass: KClass<*>) {
            if (add(kClass)) {
                val properties =
                    host.classMembers(kClass).declarativeMembers
                        .filter { it.kind == MemberKind.READ_ONLY_PROPERTY || (it.kind == MemberKind.FUNCTION && it.name.startsWith("get") && it.parameters.isEmpty()) }
                val typesFromGetters = properties.mapNotNull { it.returnType.classifier as? KClass<*> }
                typesFromGetters.forEach(::visit)
            }
        }
        visit(kClass)
    }
}


private
class TypesafeProjectPropertyProducer : PropertyExtractor {
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<PropertyExtractionResult> =
        if (kClass.isGeneratedAccessors()) {
            DefaultPropertyExtractor().extractProperties(host, kClass, propertyNamePredicate)
        } else emptyList()
}


private
fun KClass<*>.isGeneratedAccessors() =
    // TODO: find a better way to filter the accessor types
    qualifiedName.orEmpty().startsWith("org.gradle.accessors.dm.")
