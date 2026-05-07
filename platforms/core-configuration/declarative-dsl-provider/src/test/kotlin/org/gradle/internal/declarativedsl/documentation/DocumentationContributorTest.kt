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
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.SchemaDocumentation
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


class DocumentationContributorTest {

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
    fun `loads catalog and grafts SchemaDocumentation onto matching schema items`() {
        val classLoader = classLoaderWith(sampleA)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()

        val grafted = loadAndGraftDocumentation(schema, classLoader, logger)

        val type = grafted.dataClassTypesByFqName[checkstyleFqn] as DataClass
        val typeDoc = type.metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals(
            "Configuration for the Checkstyle quality check on a source set.",
            typeDoc.text
        )
        val ignoreFailuresDoc = type.properties.single { it.name == "ignoreFailures" }
            .metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals("Whether Checkstyle violations should fail the build.", ignoreFailuresDoc.text)
        verifyNoInteractions(logger)
    }

    @Test
    fun `parent classloader catalog is found by getResources walk`() {
        val parent = classLoaderWith(sampleA)
        val child = URLClassLoader(emptyArray(), parent)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()

        val grafted = loadAndGraftDocumentation(schema, child, logger)

        val type = grafted.dataClassTypesByFqName[checkstyleFqn] as DataClass
        assertNotNull(type.metadata.filterIsInstance<SchemaDocumentation>().singleOrNull())
        verifyNoInteractions(logger)
    }

    @Test
    fun `conflicting catalogs follow last wins and the loser warns`() {
        val firstVersion = """
            { "types": { "$checkstyleJvmName": { "documentation": "First version." } } }
        """.trimIndent()
        val secondVersion = """
            { "types": { "$checkstyleJvmName": { "documentation": "Overriding version." } } }
        """.trimIndent()
        val classLoader = classLoaderWith(firstVersion, secondVersion)
        val schema = checkstyleSchema()
        val logger = mock<Logger>()

        val grafted = loadAndGraftDocumentation(schema, classLoader, logger)

        val type = grafted.dataClassTypesByFqName[checkstyleFqn] as DataClass
        val doc = type.metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals("Overriding version.", doc.text)

        val captor = argumentCaptor<String>()
        verify(logger).warn(captor.capture())
        assertTrue(captor.firstValue.contains(checkstyleJvmName))
        assertTrue(captor.firstValue.contains("overridden"))
    }


    private val checkstyleFqn = DefaultFqName.parse("org.gradle.api.plugins.checkstyle.CheckstyleDefinition")
    private val checkstyleJvmName = "org.gradle.api.plugins.checkstyle.CheckstyleDefinition"

    private val sampleA = """
        {
          "types": {
            "$checkstyleJvmName": {
              "documentation": "Configuration for the Checkstyle quality check on a source set.",
              "properties": {
                "ignoreFailures": "Whether Checkstyle violations should fail the build."
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
        val dir = Files.createTempDirectory("doc-contributor-test")
        tempDirs.add(dir)
        return dir
    }

    private fun checkstyleSchema(): AnalysisSchema {
        val ignoreFailures = property("ignoreFailures", DataTypeInternal.DefaultBooleanDataType)
        val checkstyle = dataClass(
            checkstyleFqn,
            checkstyleJvmName,
            properties = listOf(ignoreFailures)
        )
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return DefaultAnalysisSchema(
            topLevelReceiverType = placeholder,
            dataClassTypesByFqName = mapOf(Pair(checkstyleFqn, checkstyle), Pair(placeholder.name, placeholder)),
            genericSignaturesByFqName = emptyMap(),
            genericInstantiationsByFqName = emptyMap(),
            externalFunctionsByFqName = emptyMap(),
            infixFunctionsByFqName = emptyMap(),
            externalObjectsByFqName = emptyMap(),
            defaultImports = emptySet(),
            assignmentAugmentationsByTypeName = emptyMap()
        )
    }

    private fun property(name: String, type: DataType.PrimitiveType): DataProperty =
        DefaultDataProperty(
            name = name,
            valueType = DataTypeRefInternal.DefaultType(type),
            mode = DefaultDataProperty.DefaultPropertyMode.DefaultReadWrite,
            hasDefaultValue = false
        )

    private fun dataClass(fqn: FqName, javaTypeName: String, properties: List<DataProperty> = emptyList()): DefaultDataClass =
        DefaultDataClass(
            name = fqn,
            javaTypeName = javaTypeName,
            javaTypeArgumentTypeNames = emptyList(),
            supertypes = emptySet(),
            properties = properties,
            memberFunctions = emptyList(),
            constructors = emptyList(),
            metadata = emptyList()
        )
}
