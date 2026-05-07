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

package org.gradle.internal.declarativedsl.documentation

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.SchemaItemMetadata
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataBuilderFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultEnumClass
import org.gradle.internal.declarativedsl.analysis.SchemaItemMetadataInternal


/**
 * Returns a copy of the given schema with `SchemaDocumentation` metadata grafted onto every item
 * that is referenced by the catalog. The original schema is not mutated.
 */
fun graftDocumentation(schema: AnalysisSchema, catalog: DocumentationCatalog): AnalysisSchema {
    val original = schema as DefaultAnalysisSchema
    val withCatalogEntries = original.dataClassTypesByFqName.mapValues { (_, classType) ->
        graftClassType(classType, catalog, original)
    }
    val withConfigureMirror = withCatalogEntries.mapValues { (_, classType) ->
        mirrorConfigureFromGetter(classType)
    }
    val rebuiltTopLevelReceiver = withConfigureMirror[original.topLevelReceiverType.name] as? DataClass
        ?: original.topLevelReceiverType
    val rebuiltInstantiations = original.genericInstantiationsByFqName.mapValues { (_, byArguments) ->
        byArguments.mapValues { (_, instantiation) ->
            withConfigureMirror[instantiation.name] ?: instantiation
        }
    }
    return original.copy(
        topLevelReceiverType = rebuiltTopLevelReceiver,
        dataClassTypesByFqName = withConfigureMirror,
        genericInstantiationsByFqName = rebuiltInstantiations
    )
}


private fun mirrorConfigureFromGetter(classType: DataType.ClassDataType): DataType.ClassDataType {
    if (classType !is DefaultDataClass) return classType
    val docByPropertyName = classType.properties.associate { property ->
        property.name to property.metadata.filterIsInstance<SchemaItemMetadataInternal.DefaultSchemaDocumentation>().firstOrNull()
    }
    val updatedFunctions = classType.memberFunctions.map { function ->
        val isConfigureFromGetter = function.metadata.any { it is SchemaItemMetadataInternal.SchemaMemberOriginInternal.DefaultConfigureFromGetterOrigin }
        val alreadyDocumented = function.metadata.any { it is SchemaItemMetadataInternal.DefaultSchemaDocumentation }
        if (!isConfigureFromGetter || alreadyDocumented) {
            function
        } else {
            val mirrored = docByPropertyName[function.simpleName] ?: return@map function
            when (function) {
                is DefaultDataMemberFunction -> function.copy(metadata = function.metadata + mirrored)
                is DefaultDataBuilderFunction -> function.copy(metadata = function.metadata + mirrored)
                else -> function
            }
        }
    }
    return classType.copy(memberFunctions = updatedFunctions)
}


private fun graftClassType(
    classType: DataType.ClassDataType,
    catalog: DocumentationCatalog,
    schema: AnalysisSchema
): DataType.ClassDataType = when (classType) {
    is DefaultDataClass -> {
        val entry = catalog.types[classType.javaTypeName]
        if (entry == null) {
            classType
        } else {
            classType.copy(
                properties = classType.properties.map { graftProperty(it, entry) },
                memberFunctions = classType.memberFunctions.map { graftMemberFunction(it, entry, schema) },
                metadata = classType.metadata.withDocumentation(entry.documentation, parts = emptyMap())
            )
        }
    }
    is DefaultEnumClass -> {
        val entry = catalog.enums[classType.javaTypeName]
        if (entry == null) {
            classType
        } else {
            classType.copy(metadata = classType.metadata.withDocumentation(entry.documentation, parts = entry.entries))
        }
    }
    else -> classType
}


private fun graftProperty(property: DataProperty, entry: TypeDocumentation): DataProperty {
    val text = entry.properties[property.name] ?: return property
    val default = property as DefaultDataProperty
    return default.copy(metadata = default.metadata.withDocumentation(text, parts = emptyMap()))
}


private fun graftMemberFunction(
    function: SchemaMemberFunction,
    entry: TypeDocumentation,
    schema: AnalysisSchema
): SchemaMemberFunction {
    val key = functionKey(function.simpleName, function.parameters, schema)
    val functionEntry = entry.functions[key] ?: return function
    val newMetadata = function.metadata.withDocumentation(
        text = functionEntry.documentation,
        parts = functionEntry.parameters
    )
    return when (function) {
        is DefaultDataMemberFunction -> function.copy(metadata = newMetadata)
        is DefaultDataBuilderFunction -> function.copy(metadata = newMetadata)
        else -> function
    }
}


private fun List<SchemaItemMetadata>.withDocumentation(text: String?, parts: Map<String, String>): List<SchemaItemMetadata> {
    val withoutPrevious = filterNot { it is SchemaItemMetadataInternal.DefaultSchemaDocumentation }
    return if (text == null && parts.isEmpty()) {
        withoutPrevious
    } else {
        withoutPrevious + SchemaItemMetadataInternal.DefaultSchemaDocumentation(text = text, parts = parts)
    }
}
