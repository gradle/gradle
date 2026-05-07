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
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path


class DocumentationCatalogLoaderTest {

    private lateinit var tempDirs: MutableList<Path>

    @Before
    fun setUp() {
        tempDirs = mutableListOf()
    }

    @After
    fun tearDown() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    @Test
    fun `loads a single catalog resource`() {
        val classLoader = classLoaderWith(sampleA)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()
        val loader = DocumentationCatalogLoader(logger)

        val catalog = loader.load(classLoader, schema)

        val entry = catalog.types[checkstyleJvmName]
        assertNotNull(entry)
        assertEquals(
            "Configuration for the Checkstyle quality check on a source set.",
            entry!!.documentation
        )
        assertEquals(
            mapOf(
                Pair("ignoreFailures", "Whether Checkstyle violations should fail the build."),
                Pair("configFile", "Path to the Checkstyle configuration XML file.")
            ),
            entry.properties
        )
        verifyNoInteractions(logger)
    }

    @Test
    fun `filters orphan keys silently and emits no warning`() {
        val sampleE = """
            {
              "types": {
                "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
                  "properties": {
                    "ignoreFailures": "Valid.",
                    "removedProperty": "This property no longer exists in the schema."
                  }
                },
                "org.example.NotInSchema": {
                  "documentation": "This type is not in the schema."
                }
              }
            }
        """.trimIndent()
        val classLoader = classLoaderWith(sampleE)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()
        val loader = DocumentationCatalogLoader(logger)

        val catalog = loader.load(classLoader, schema)

        assertEquals(setOf(checkstyleJvmName), catalog.types.keys)
        val checkstyleEntry = catalog.types[checkstyleJvmName]!!
        assertEquals(mapOf(Pair("ignoreFailures", "Valid.")), checkstyleEntry.properties)
        verifyNoInteractions(logger)
    }

    @Test
    fun `conflicting entries follow last wins and the loser gets a warning`() {
        val firstVersion = """
            { "types": { "$checkstyleJvmName": { "documentation": "First version." } } }
        """.trimIndent()
        val secondVersion = """
            { "types": { "$checkstyleJvmName": { "documentation": "Overriding version." } } }
        """.trimIndent()
        val classLoader = classLoaderWith(firstVersion, secondVersion)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()
        val loader = DocumentationCatalogLoader(logger)

        val catalog = loader.load(classLoader, schema)

        assertEquals("Overriding version.", catalog.types[checkstyleJvmName]?.documentation)

        val captor = argumentCaptor<String>()
        verify(logger).warn(captor.capture())
        val message = captor.firstValue
        assertTrue(message.contains(checkstyleJvmName), "Warning should name the overridden key, was: $message")
        assertTrue(message.contains("overridden"), "Warning should mention overrides, was: $message")
    }

    @Test
    fun `malformed JSON resource warns and other catalogs still load`() {
        val malformed = "{ this is not json }"
        val classLoader = classLoaderWith(malformed, sampleA)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()
        val loader = DocumentationCatalogLoader(logger)

        val catalog = loader.load(classLoader, schema)

        val entry = catalog.types[checkstyleJvmName]
        assertNotNull(entry)
        assertEquals(
            "Configuration for the Checkstyle quality check on a source set.",
            entry!!.documentation
        )

        val captor = argumentCaptor<String>()
        verify(logger).warn(captor.capture())
        val message = captor.firstValue
        assertTrue(message.contains("parse error"), "Warning should mention parse error, was: $message")
    }

    @Test
    fun `unknown top level JSON fields are accepted with no warning`() {
        val sampleH = """
            {
              "types": {
                "$checkstyleJvmName": { "documentation": "Hello." }
              },
              "futureExtensionPoint": { "x": "y" }
            }
        """.trimIndent()
        val classLoader = classLoaderWith(sampleH)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()
        val loader = DocumentationCatalogLoader(logger)

        val catalog = loader.load(classLoader, schema)

        assertEquals("Hello.", catalog.types[checkstyleJvmName]?.documentation)
        verifyNoInteractions(logger)
    }

    @Test
    fun `conflict warning truncates the key list at ten`() {
        val schema = manyTypesSchema(15)
        val firstVersion = catalogJsonForTypes((1..15).map { typeJvmName(it) }, "first")
        val secondVersion = catalogJsonForTypes((1..15).map { typeJvmName(it) }, "second")
        val classLoader = classLoaderWith(firstVersion, secondVersion)
        val logger = mock<Logger>()
        val loader = DocumentationCatalogLoader(logger)

        loader.load(classLoader, schema)

        val captor = argumentCaptor<String>()
        verify(logger).warn(captor.capture())
        val message = captor.firstValue
        assertTrue(message.contains("(15):"), "Warning should report the full count, was: $message")
        assertTrue(message.contains("(+5 more)"), "Warning should report the truncated remainder, was: $message")
    }

    @Test
    fun `merges multiple non-conflicting catalogs from separate resources`() {
        val checkstyleCatalog = """
            {
              "types": {
                "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
                  "documentation": "Checkstyle config."
                }
              }
            }
        """.trimIndent()
        val antlrCatalog = """
            {
              "types": {
                "org.gradle.api.plugins.antlr.AntlrGrammarsDefinition": {
                  "documentation": "ANTLR grammars binding.",
                  "properties": {
                    "trace": "Enable trace output during parsing.",
                    "traceLexer": "Enable trace output for the lexer."
                  }
                }
              }
            }
        """.trimIndent()
        val classLoader = classLoaderWith(checkstyleCatalog, antlrCatalog)
        val schema = schemaWithCheckstyleAndAntlr()
        val logger = mock<Logger>()
        val loader = DocumentationCatalogLoader(logger)

        val catalog = loader.load(classLoader, schema)

        assertEquals(
            "Checkstyle config.",
            catalog.types[checkstyleJvmName]?.documentation
        )
        val antlrEntry = catalog.types[antlrJvmName]!!
        assertEquals("ANTLR grammars binding.", antlrEntry.documentation)
        assertEquals(
            mapOf(
                Pair("trace", "Enable trace output during parsing."),
                Pair("traceLexer", "Enable trace output for the lexer.")
            ),
            antlrEntry.properties
        )
        verifyNoInteractions(logger)
    }


    private val checkstyleFqn = DefaultFqName.parse("org.gradle.api.plugins.checkstyle.CheckstyleDefinition")
    private val checkstyleJvmName = "org.gradle.api.plugins.checkstyle.CheckstyleDefinition"
    private val antlrFqn = DefaultFqName.parse("org.gradle.api.plugins.antlr.AntlrGrammarsDefinition")
    private val antlrJvmName = "org.gradle.api.plugins.antlr.AntlrGrammarsDefinition"

    private val sampleA = """
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

    private fun classLoaderWith(vararg catalogJsons: String): ClassLoader {
        val urls = catalogJsons.map { json ->
            val dir = newTempDir()
            val resourcePath = dir.resolve("META-INF/declarative-dsl/documentation.json")
            Files.createDirectories(resourcePath.parent)
            Files.writeString(resourcePath, json)
            dir.toUri().toURL()
        }
        return URLClassLoader(urls.toTypedArray(), null)
    }

    private fun newTempDir(): Path {
        val dir = Files.createTempDirectory("docs-loader-test")
        tempDirs.add(dir)
        return dir
    }

    private fun checkstyleSchema(): AnalysisSchema {
        val ignoreFailures = property("ignoreFailures", DataTypeInternal.DefaultBooleanDataType)
        val configFile = property("configFile", DataTypeInternal.DefaultStringDataType)
        val checkstyle = dataClass(
            checkstyleFqn,
            checkstyleJvmName,
            properties = listOf(ignoreFailures, configFile)
        )
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return schemaWith(
            topLevelReceiver = placeholder,
            types = mapOf(Pair(checkstyleFqn, checkstyle), Pair(placeholder.name, placeholder))
        )
    }

    private fun typeJvmName(index: Int): String = "org.example.demo.Type$index"

    private fun manyTypesSchema(count: Int): AnalysisSchema {
        val types = (1..count).associate { i ->
            val fqn = DefaultFqName.parse(typeJvmName(i))
            Pair(fqn, dataClass(fqn, typeJvmName(i)) as DataType.ClassDataType)
        }
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return schemaWith(
            topLevelReceiver = placeholder,
            types = types + mapOf(Pair(placeholder.name, placeholder as DataType.ClassDataType))
        )
    }

    private fun catalogJsonForTypes(jvmNames: List<String>, doc: String): String {
        val entries = jvmNames.joinToString(",\n    ") { name ->
            "\"$name\": { \"documentation\": \"$doc\" }"
        }
        return "{\n  \"types\": {\n    $entries\n  }\n}"
    }

    private fun schemaWithCheckstyleAndAntlr(): AnalysisSchema {
        val checkstyle = dataClass(checkstyleFqn, checkstyleJvmName)
        val trace = property("trace", DataTypeInternal.DefaultBooleanDataType)
        val traceLexer = property("traceLexer", DataTypeInternal.DefaultBooleanDataType)
        val antlr = dataClass(
            antlrFqn,
            antlrJvmName,
            properties = listOf(trace, traceLexer)
        )
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return schemaWith(
            topLevelReceiver = placeholder,
            types = mapOf(
                Pair(checkstyleFqn, checkstyle),
                Pair(antlrFqn, antlr),
                Pair(placeholder.name, placeholder)
            )
        )
    }

    private fun property(name: String, type: DataType.PrimitiveType): DataProperty =
        DefaultDataProperty(
            name = name,
            valueType = DataTypeRefInternal.DefaultType(type),
            mode = DefaultDataProperty.DefaultPropertyMode.DefaultReadWrite,
            hasDefaultValue = false
        )

    private fun dataClass(
        fqn: FqName,
        javaTypeName: String,
        properties: List<DataProperty> = emptyList()
    ): DefaultDataClass = DefaultDataClass(
        name = fqn,
        javaTypeName = javaTypeName,
        javaTypeArgumentTypeNames = emptyList(),
        supertypes = emptySet(),
        properties = properties,
        memberFunctions = emptyList(),
        constructors = emptyList(),
        metadata = emptyList()
    )

    private fun schemaWith(
        topLevelReceiver: DefaultDataClass,
        types: Map<FqName, DataType.ClassDataType>
    ): AnalysisSchema = DefaultAnalysisSchema(
        topLevelReceiverType = topLevelReceiver,
        dataClassTypesByFqName = types,
        genericSignaturesByFqName = emptyMap(),
        genericInstantiationsByFqName = emptyMap(),
        externalFunctionsByFqName = emptyMap(),
        infixFunctionsByFqName = emptyMap(),
        externalObjectsByFqName = emptyMap(),
        defaultImports = emptySet(),
        assignmentAugmentationsByTypeName = emptyMap()
    )
}
