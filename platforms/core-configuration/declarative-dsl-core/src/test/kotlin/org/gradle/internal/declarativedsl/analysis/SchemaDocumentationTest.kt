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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.SchemaDocumentation
import org.gradle.declarative.dsl.schema.SchemaItemMetadata
import org.gradle.internal.declarativedsl.serialization.SchemaSerialization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class SchemaDocumentationTest {

    @Test
    fun `SchemaDocumentation is a SchemaItemMetadata carrying text and named parts`() {
        val parts = mapOf(Pair("a", "Doc for a."), Pair("b", "Doc for b."))
        val doc: SchemaItemMetadata = SchemaItemMetadataInternal.DefaultSchemaDocumentation(
            text = "Type level doc.",
            parts = parts
        )

        assertTrue(doc is SchemaDocumentation)
        val asDoc = doc as SchemaDocumentation
        assertEquals("Type level doc.", asDoc.text)
        assertEquals(parts, asDoc.parts)
    }

    @Test
    fun `SchemaDocumentation round trips through schema serialization`() {
        val parts = mapOf(Pair("entryA", "Entry A doc."), Pair("entryB", "Entry B doc."))
        val doc = SchemaItemMetadataInternal.DefaultSchemaDocumentation(
            text = "Type level doc.",
            parts = parts
        )
        val testTypeName = DefaultFqName.parse("org.example.Test")
        val testType = DefaultDataClass(
            name = testTypeName,
            javaTypeName = "org.example.Test",
            javaTypeArgumentTypeNames = emptyList(),
            supertypes = emptySet(),
            properties = emptyList(),
            memberFunctions = emptyList(),
            constructors = emptyList(),
            metadata = listOf(doc)
        )
        val schema = DefaultAnalysisSchema(
            topLevelReceiverType = testType,
            dataClassTypesByFqName = mapOf(testTypeName to testType),
            genericSignaturesByFqName = emptyMap(),
            genericInstantiationsByFqName = emptyMap(),
            externalFunctionsByFqName = emptyMap(),
            infixFunctionsByFqName = emptyMap(),
            externalObjectsByFqName = emptyMap(),
            defaultImports = emptySet(),
            assignmentAugmentationsByTypeName = emptyMap()
        )

        val json = SchemaSerialization.schemaToJsonString(schema)
        val deserialized = SchemaSerialization.schemaFromJsonString(json)

        val deserializedType = deserialized.dataClassTypesByFqName[testTypeName]!!
        val deserializedDoc = deserializedType.metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals("Type level doc.", deserializedDoc.text)
        assertEquals(parts, deserializedDoc.parts)
    }
}
