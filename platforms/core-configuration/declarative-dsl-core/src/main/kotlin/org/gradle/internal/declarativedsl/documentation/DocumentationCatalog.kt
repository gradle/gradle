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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


/**
 * On-disk shape of a `META-INF/declarative-dsl/documentation.json` resource.
 *
 * Top level: documented types and enums, keyed by JVM-binary FQN.
 */
@Serializable
data class DocumentationCatalog(
    val types: Map<String, TypeDocumentation> = emptyMap(),
    val enums: Map<String, EnumDocumentation> = emptyMap()
)


/**
 * Documentation for a class type: the type itself, its properties, and its member functions.
 */
@Serializable
data class TypeDocumentation(
    val documentation: String? = null,
    val properties: Map<String, String> = emptyMap(),
    val functions: Map<String, FunctionDocumentation> = emptyMap()
)


/**
 * Documentation for a function: the function itself plus its parameters by name.
 */
@Serializable
data class FunctionDocumentation(
    val documentation: String? = null,
    val parameters: Map<String, String> = emptyMap()
)


/**
 * Documentation for an enum type: the type itself plus its entries by name.
 */
@Serializable
data class EnumDocumentation(
    val documentation: String? = null,
    val entries: Map<String, String> = emptyMap()
)


private val documentationCatalogJson: Json = Json {
    ignoreUnknownKeys = true
}


/**
 * Parses a JSON string into a `DocumentationCatalog`. Unknown fields are ignored so the format can
 * evolve additively.
 */
fun parseDocumentationCatalog(json: String): DocumentationCatalog =
    documentationCatalogJson.decodeFromString(json)
