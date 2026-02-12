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

import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.SettingsInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
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
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

internal fun EvaluationSchemaBuilder.versionCatalogs(settings: SettingsInternal) {
    registerAnalysisSchemaComponent(VersionCatalogsComponent(settings))
}

class VersionCatalogsComponent(private val settings: SettingsInternal) : AnalysisSchemaComponent {
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


}

private fun String.sanitizeCatalogEntry(): String = this
