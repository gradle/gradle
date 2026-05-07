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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class DocumentationCatalogTest {

    @Test
    fun `Sample A parses into the expected catalog tree`() {
        val sampleA = """
            {
              "types": {
                "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
                  "documentation": "Configuration for the Checkstyle quality check on a source set.",
                  "properties": {
                    "ignoreFailures": "Whether Checkstyle violations should fail the build.",
                    "configFile": "Path to the Checkstyle configuration XML file."
                  }
                }
              }
            }
        """.trimIndent()

        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleA)

        assertTrue(catalog.enums.isEmpty())
        val checkstyleEntry = catalog.types["org.gradle.api.plugins.checkstyle.CheckstyleDefinition"]!!
        assertEquals(
            "Configuration for the Checkstyle quality check on a source set.",
            checkstyleEntry.documentation
        )
        assertEquals(
            mapOf(
                Pair("ignoreFailures", "Whether Checkstyle violations should fail the build."),
                Pair("configFile", "Path to the Checkstyle configuration XML file.")
            ),
            checkstyleEntry.properties
        )
        assertTrue(checkstyleEntry.functions.isEmpty())
    }

    @Test
    fun `Sample H parses successfully and the unknown top level field is ignored`() {
        val sampleH = """
            {
              "types": {
                "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": { "documentation": "Hello." }
              },
              "futureExtensionPoint": { "x": "y" }
            }
        """.trimIndent()

        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleH)

        val entry = catalog.types["org.gradle.api.plugins.checkstyle.CheckstyleDefinition"]!!
        assertEquals("Hello.", entry.documentation)
        assertTrue(entry.properties.isEmpty())
        assertTrue(entry.functions.isEmpty())
    }

    @Test
    fun `function and enum entries deserialize with their nested maps`() {
        val raw = """
            {
              "types": {
                "org.example.Foo": {
                  "functions": {
                    "doX(java.lang.String,int)": {
                      "documentation": "Does X.",
                      "parameters": { "name": "name doc", "depth": "depth doc" }
                    }
                  }
                }
              },
              "enums": {
                "org.example.Color": {
                  "documentation": "Color enum.",
                  "entries": { "RED": "red doc", "BLUE": "blue doc" }
                }
              }
            }
        """.trimIndent()

        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(raw)

        val fooFunc = catalog.types["org.example.Foo"]!!.functions["doX(java.lang.String,int)"]!!
        assertEquals("Does X.", fooFunc.documentation)
        assertEquals(
            mapOf(Pair("name", "name doc"), Pair("depth", "depth doc")),
            fooFunc.parameters
        )

        val colorEnum = catalog.enums["org.example.Color"]!!
        assertEquals("Color enum.", colorEnum.documentation)
        assertEquals(
            mapOf(Pair("RED", "red doc"), Pair("BLUE", "blue doc")),
            colorEnum.entries
        )
    }

    @Test
    fun `empty catalog deserializes to empty maps`() {
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>("{}")

        assertTrue(catalog.types.isEmpty())
        assertTrue(catalog.enums.isEmpty())
    }

    @Test
    fun `omitted optional fields fall back to defaults`() {
        val raw = """{ "types": { "org.example.Foo": {} } }"""

        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(raw)

        val entry = catalog.types["org.example.Foo"]!!
        assertNull(entry.documentation)
        assertTrue(entry.properties.isEmpty())
        assertTrue(entry.functions.isEmpty())
    }
}
