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

import org.gradle.api.logging.Logger
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.EnumClass
import java.net.URL


private const val CATALOG_RESOURCE_PATH = "META-INF/declarative-dsl/documentation.json"
private const val RESOURCE_SUFFIX = "!/$CATALOG_RESOURCE_PATH"


/**
 * Reads `META-INF/declarative-dsl/documentation.json` resources from a class loader and merges them
 * into a single `DocumentationCatalog`.
 */
class DocumentationCatalogLoader(private val logger: Logger) {

    fun load(classLoader: ClassLoader, schema: AnalysisSchema): DocumentationCatalog {
        val resources = classLoader.getResources(CATALOG_RESOURCE_PATH).toList()
        val parseErrors = mutableMapOf<String, String>()
        val parsed = resources.mapNotNull { url ->
            val resourceId = displayPathOf(url)
            try {
                ParsedResource(resourceId, readCatalog(url))
            } catch (e: Exception) {
                parseErrors[resourceId] = e.message ?: e::class.java.simpleName
                null
            }
        }
        val (merged, conflictsByLoser) = mergeWithConflictTracking(parsed)
        emitWarnings(parseErrors, conflictsByLoser)
        return filterOrphans(merged, schema)
    }

    private fun readCatalog(url: URL): DocumentationCatalog {
        val text = url.openStream().use { it.bufferedReader().readText() }
        return parseDocumentationCatalog(text)
    }

    private fun emitWarnings(
        parseErrors: Map<String, String>,
        conflictsByLoser: Map<String, List<String>>
    ) {
        parseErrors.forEach { (resourceId, message) ->
            logger.warn(formatParseErrorWarning(resourceId, message))
        }
        conflictsByLoser.forEach { (resourceId, overriddenKeys) ->
            logger.warn(formatConflictWarning(resourceId, overriddenKeys))
        }
    }
}


private data class ParsedResource(val resourceId: String, val catalog: DocumentationCatalog)


private fun mergeWithConflictTracking(
    resources: List<ParsedResource>
): Pair<DocumentationCatalog, Map<String, List<String>>> {
    val mergedTypes = mutableMapOf<String, TypeDocumentation>()
    val mergedEnums = mutableMapOf<String, EnumDocumentation>()
    val typeOrigins = mutableMapOf<String, String>()
    val enumOrigins = mutableMapOf<String, String>()
    val conflicts = mutableMapOf<String, MutableList<String>>()

    resources.forEach { (resourceId, catalog) ->
        catalog.types.forEach { (key, value) ->
            val previousOrigin = typeOrigins[key]
            if (previousOrigin != null) {
                conflicts.getOrPut(previousOrigin) { mutableListOf() }.add(key)
            }
            mergedTypes[key] = value
            typeOrigins[key] = resourceId
        }
        catalog.enums.forEach { (key, value) ->
            val previousOrigin = enumOrigins[key]
            if (previousOrigin != null) {
                conflicts.getOrPut(previousOrigin) { mutableListOf() }.add(key)
            }
            mergedEnums[key] = value
            enumOrigins[key] = resourceId
        }
    }
    return DocumentationCatalog(types = mergedTypes, enums = mergedEnums) to conflicts
}


private const val WARNING_KEY_LIMIT = 10


private fun formatConflictWarning(resourceId: String, overriddenKeys: List<String>): String {
    return buildString {
        append("Documentation issues in ").append(resourceId).append(":\n")
        append("  keys overridden by other catalogs (")
        append(overriddenKeys.size)
        append("): ")
        append(formatKeyList(overriddenKeys))
    }
}


private fun formatKeyList(keys: List<String>): String =
    if (keys.size <= WARNING_KEY_LIMIT) {
        keys.joinToString(", ")
    } else {
        keys.take(WARNING_KEY_LIMIT).joinToString(", ") + " (+${keys.size - WARNING_KEY_LIMIT} more)"
    }


private fun formatParseErrorWarning(resourceId: String, errorMessage: String): String =
    buildString {
        append("Documentation issues in ").append(resourceId).append(":\n")
        append("  parse error: ").append(errorMessage)
    }


private fun displayPathOf(url: URL): String =
    url.toExternalForm().removeSuffix(RESOURCE_SUFFIX).removeSuffix("/$CATALOG_RESOURCE_PATH")


private fun filterOrphans(catalog: DocumentationCatalog, schema: AnalysisSchema): DocumentationCatalog {
    val schemaIndex = SchemaIndex.from(schema)
    val filteredTypes = catalog.types
        .filter { (jvmName, _) -> schemaIndex.classByJvmName.containsKey(jvmName) }
        .mapValues { (jvmName, entry) ->
            val classType = schemaIndex.classByJvmName.getValue(jvmName)
            entry.copy(
                properties = entry.properties.filter { (name, _) ->
                    classType.properties.any { it.name == name }
                },
                functions = entry.functions.filter { (key, _) ->
                    schemaIndex.functionKeysByJvmName.getOrDefault(jvmName, emptySet()).contains(key)
                }
            )
        }
    val filteredEnums = catalog.enums
        .filter { (jvmName, _) -> schemaIndex.enumByJvmName.containsKey(jvmName) }
        .mapValues { (jvmName, entry) ->
            val enumType = schemaIndex.enumByJvmName.getValue(jvmName)
            entry.copy(
                entries = entry.entries.filter { (name, _) -> enumType.entryNames.contains(name) }
            )
        }
    return DocumentationCatalog(types = filteredTypes, enums = filteredEnums)
}


private class SchemaIndex(
    val classByJvmName: Map<String, DataClass>,
    val enumByJvmName: Map<String, EnumClass>,
    val functionKeysByJvmName: Map<String, Set<String>>
) {
    companion object {
        fun from(schema: AnalysisSchema): SchemaIndex {
            val classByJvmName = mutableMapOf<String, DataClass>()
            val enumByJvmName = mutableMapOf<String, EnumClass>()
            val functionKeysByJvmName = mutableMapOf<String, Set<String>>()
            schema.dataClassTypesByFqName.values.forEach { classType ->
                when (classType) {
                    is DataClass -> {
                        classByJvmName[classType.javaTypeName] = classType
                        functionKeysByJvmName[classType.javaTypeName] =
                            classType.memberFunctions.map { functionKey(it.simpleName, it.parameters, schema) }.toSet()
                    }
                    is EnumClass -> enumByJvmName[classType.javaTypeName] = classType
                    else -> Unit
                }
            }
            return SchemaIndex(classByJvmName, enumByJvmName, functionKeysByJvmName)
        }
    }
}
