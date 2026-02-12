/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.internal.SettingsInternal
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.LossySchemaBuildingOperation
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractionMetadata
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelClass
import org.gradle.internal.declarativedsl.schemaBuilder.orError
import org.gradle.internal.management.VersionCatalogBuilderInternal
import java.util.Optional
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.typeOf

internal fun EvaluationSchemaBuilder.versionCatalogs(settings: SettingsInternal) {
    val versionCatalogsComponent = VersionCatalogsComponent(settings)
    registerAnalysisSchemaComponent(versionCatalogsComponent)
    ifConversionSupported {
        registerObjectConversionComponent(versionCatalogsComponent)
    }
}

class VersionCatalogsComponent(private val settings: SettingsInternal) : ObjectConversionComponent, AnalysisSchemaComponent {
    override fun propertyExtractors(): List<PropertyExtractor> = listOf(object : PropertyExtractor {
        override fun extractProperties(
            host: SchemaBuildingHost,
            kClass: KClass<*>,
            propertyNamePredicate: (String) -> Boolean
        ): Iterable<PropertyExtractionResult> {
            host.inContextOfModelClass(kClass) {
                return settings.dependencyResolutionManagement.versionCatalogs.map { catalogBuilder ->
                    catalogBuilder as VersionCatalogBuilderInternal

                    val catalog = catalogBuilder.build()

                    val catalogName = catalog.name

                    val valueTypeRef = host.syntheticTypeRegistrar.registerSyntheticType(
                        "versionCatalogs:$catalogName"
                    ) {
                        DefaultDataClass(
                            name = DefaultFqName.parse("versionCatalogs$$catalogName"),
                            javaTypeName = VersionCatalog::class.java.name, // TODO: give the tooling a proper hint to the catalog source
                            javaTypeArgumentTypeNames = emptyList(),
                            supertypes = emptySet(),
                            properties = emptyList(),
                            memberFunctions = catalog.libraryAliases.map { catalogEntryFunction(host, it) },
                            constructors = emptyList()
                        )
                    }

                    ExtractionResult.Extracted(
                        DefaultDataProperty(catalogName, valueTypeRef, DefaultDataProperty.DefaultPropertyMode.DefaultReadOnly, true),
                        PropertyExtractionMetadata(emptyList(), null)
                    )
                }
            }
        }
    })

    @OptIn(LossySchemaBuildingOperation::class) // Using a fixed type
    private fun catalogEntryFunction(host: SchemaBuildingHost, name: String) = DefaultDataMemberFunction(
        host.containerTypeRef(VersionCatalog::class).orError(),
        name.sanitizeCatalogEntry(),
        emptyList(),
        false,
        FunctionSemanticsInternal.DefaultPure(host.typeRef(typeOf<Dependency>()).orError())
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(object : FunctionExtractor {
        override fun memberFunctions(
            host: SchemaBuildingHost,
            kClass: KClass<*>,
            preIndex: DataSchemaBuilder.PreIndex
        ): Iterable<FunctionExtractionResult> = emptyList()
    })

    override fun runtimePropertyResolvers(): List<RuntimePropertyResolver> =
        listOf(VersionCatalogsRuntimePropertyResolver())

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> =
        listOf(VersionCatalogsRuntimeFunctionResolver())
}

/**
 * Resolves [top-level references to the version catalogs container][ProjectTopLevelReceiver.catalogs], i.e. `project.catalogs`,
 * and subsequent `catalogs.<version catalog name>` property references.
 *
 * @see VersionCatalogsRuntimeFunction
 */
private class VersionCatalogsRuntimePropertyResolver : RuntimePropertyResolver {

    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.ReadResolution {
        return when {
            receiverClass.isSubclassOf(Project::class) && name == "catalogs" -> {
                RuntimePropertyResolver.ReadResolution.ResolvedRead { receiver ->
                    val catalogs: VersionCatalogsExtension? = (receiver as Project).extensions.findByName("versionCatalogs") as? VersionCatalogsExtension
                    catalogs?.let {
                        InstanceAndPublicType.of(RuntimeVersionCatalogsContainer(it), VersionCatalogsContainer::class)
                    } ?: InstanceAndPublicType.NULL
                }
            }

            receiverClass.isSubclassOf(VersionCatalogsContainer::class) -> {
                RuntimePropertyResolver.ReadResolution.ResolvedRead { receiver ->
                    require(receiver is RuntimeVersionCatalogsContainer)
                    receiver.catalogs
                        .find(name)
                        .map { InstanceAndPublicType.of(RuntimeVersionCatalog(it), VersionCatalog::class) }
                        .orElse(InstanceAndPublicType.NULL)
                }
            }

            else -> RuntimePropertyResolver.ReadResolution.UnresolvedRead
        }
    }

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String) =
        RuntimePropertyResolver.WriteResolution.UnresolvedWrite
}

/**
 * Resolves version catalog function invocations, i.e., `catalog.libName()`, to a [runtime function][VersionCatalogsRuntimeFunction]
 * that returns the [dependency associated with the library named after the function][org.gradle.api.artifacts.VersionCatalog.findLibrary].
 */
private class VersionCatalogsRuntimeFunctionResolver : RuntimeFunctionResolver {

    override fun resolve(
        receiverClass: KClass<*>,
        schemaFunction: SchemaFunction,
        scopeClassLoader: ClassLoader
    ): RuntimeFunctionResolver.Resolution {
        return when {
            receiverClass.isSubclassOf(VersionCatalog::class) -> {
                RuntimeFunctionResolver.Resolution.Resolved(
                    VersionCatalogsRuntimeFunction(schemaFunction.simpleName)
                )
            }

            else -> RuntimeFunctionResolver.Resolution.Unresolved
        }
    }
}

private class VersionCatalogsRuntimeFunction(val libName: String) : DeclarativeRuntimeFunction {

    override fun callBy(
        receiver: Any?,
        binding: Map<DataParameter, Any?>,
        hasLambda: Boolean
    ): DeclarativeRuntimeFunction.InvocationResult {
        require(receiver is RuntimeVersionCatalog)
        val result = receiver.catalog.findLibrary(libName)
            .flatMap { Optional.ofNullable(it.orNull) }
            .map { InstanceAndPublicType.of(it, Dependency::class) }
            .orElse(InstanceAndPublicType.NULL)
        return DeclarativeRuntimeFunction.InvocationResult(result, InstanceAndPublicType.NULL)
    }
}

private class RuntimeVersionCatalogsContainer(val catalogs: VersionCatalogsExtension) : VersionCatalogsContainer

private class RuntimeVersionCatalog(val catalog: org.gradle.api.artifacts.VersionCatalog) : VersionCatalog

private fun String.sanitizeCatalogEntry(): String = this
