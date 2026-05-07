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
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.SchemaDocumentation
import org.gradle.declarative.dsl.schema.SchemaItemMetadata
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultEnumClass
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.SchemaItemMetadataInternal
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class GrafterTest {

    @Test
    fun `grafts type doc onto a matching DataClass`() {
        val schema = checkstyleSchema()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleA)

        val grafted = graftDocumentation(schema, catalog)

        val type = grafted.dataClassTypesByFqName[checkstyleFqn] as DataClass
        val doc = type.metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals(
            "Configuration for the Checkstyle quality check on a source set.",
            doc.text
        )
        assertTrue(doc.parts.isEmpty())
    }

    @Test
    fun `grafts property docs onto each matching DataProperty`() {
        val schema = checkstyleSchema()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleA)

        val grafted = graftDocumentation(schema, catalog)

        val type = grafted.dataClassTypesByFqName[checkstyleFqn] as DataClass
        val ignoreFailuresDoc = type.properties.single { it.name == "ignoreFailures" }
            .metadata.filterIsInstance<SchemaDocumentation>().single()
        val configFileDoc = type.properties.single { it.name == "configFile" }
            .metadata.filterIsInstance<SchemaDocumentation>().single()

        assertEquals("Whether Checkstyle violations should fail the build.", ignoreFailuresDoc.text)
        assertTrue(ignoreFailuresDoc.parts.isEmpty())
        assertEquals("Path to the Checkstyle configuration XML file.", configFileDoc.text)
        assertTrue(configFileDoc.parts.isEmpty())
    }

    @Test
    fun `properties without a catalog entry get no SchemaDocumentation`() {
        val schema = checkstyleSchema()
        val partialCatalog = """
            {
              "types": {
                "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
                  "properties": { "ignoreFailures": "Only this one." }
                }
              }
            }
        """.trimIndent()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(partialCatalog)

        val grafted = graftDocumentation(schema, catalog)

        val type = grafted.dataClassTypesByFqName[checkstyleFqn] as DataClass
        val configFile = type.properties.single { it.name == "configFile" }
        assertTrue(configFile.metadata.none { it is SchemaDocumentation })
    }

    @Test
    fun `grafts function doc and per-parameter doc onto a member function`() {
        val schema = libraryDependenciesSchema()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleC)

        val grafted = graftDocumentation(schema, catalog)

        val type = grafted.dataClassTypesByFqName[libraryDependenciesFqn] as DataClass
        val copyTo = type.memberFunctions.single { it.simpleName == "copyTo" }
        val doc = copyTo.metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals(
            "Copy all dependencies and constraints from this collector to the target.",
            doc.text
        )
        assertEquals(
            mapOf(Pair("target", "Destination receiving the copied dependencies.")),
            doc.parts
        )
    }

    @Test
    fun `grafts enum doc and per-entry docs onto a matching EnumClass`() {
        val schema = severityEnumSchema()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleJ)

        val grafted = graftDocumentation(schema, catalog)

        val enumType = grafted.dataClassTypesByFqName[severityFqn] as EnumClass
        val doc = enumType.metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals("Severity level for a quality finding.", doc.text)
        assertEquals(
            mapOf(
                Pair("INFO", "Informational; not a violation."),
                Pair("WARN", "A potential issue."),
                Pair("ERROR", "A violation that fails the build.")
            ),
            doc.parts
        )
    }

    @Test
    fun `documenting the top level receiver populates aliases consistently`() {
        val receiverFqn = DefaultFqName.parse("org.example.demo.Root")
        val receiver = dataClass(receiverFqn, "org.example.demo.Root")
        val schema = schemaWith(
            topLevelReceiver = receiver,
            types = mapOf(Pair(receiverFqn, receiver))
        )
        val raw = """
            {
              "types": {
                "org.example.demo.Root": { "documentation": "The root." }
              }
            }
        """.trimIndent()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(raw)

        val grafted = graftDocumentation(schema, catalog)

        val viaTopLevel = grafted.topLevelReceiverType.metadata.filterIsInstance<SchemaDocumentation>().single()
        val viaMap = grafted.dataClassTypesByFqName[receiverFqn]!!.metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals("The root.", viaTopLevel.text)
        assertSame(viaTopLevel, viaMap)
    }

    @Test
    fun `mirrors property doc onto getter synthesised configure function`() {
        val schema = javaLibraryModelSchema()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleM)

        val grafted = graftDocumentation(schema, catalog)

        val type = grafted.dataClassTypesByFqName[javaLibraryModelFqn] as DataClass
        val propertyDoc = type.properties.single { it.name == "testReports" }
            .metadata.filterIsInstance<SchemaDocumentation>().single()
        val configureFunctionDoc = type.memberFunctions.single { it.simpleName == "testReports" }
            .metadata.filterIsInstance<SchemaDocumentation>().single()

        assertEquals(propertyDoc.text, configureFunctionDoc.text)
        assertEquals(propertyDoc.parts, configureFunctionDoc.parts)
    }

    @Test
    fun `explicit configure function entry wins over the mirror`() {
        val schema = javaLibraryModelSchema()
        val raw = """
            {
              "types": {
                "org.gradle.api.plugins.java.JavaLibraryModel": {
                  "properties": { "testReports": "Property doc." },
                  "functions": {
                    "testReports()": { "documentation": "Explicit function doc." }
                  }
                }
              }
            }
        """.trimIndent()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(raw)

        val grafted = graftDocumentation(schema, catalog)

        val type = grafted.dataClassTypesByFqName[javaLibraryModelFqn] as DataClass
        val configureFunctionDoc = type.memberFunctions.single { it.simpleName == "testReports" }
            .metadata.filterIsInstance<SchemaDocumentation>().single()
        assertEquals("Explicit function doc.", configureFunctionDoc.text)
    }

    @Test
    fun `original schema is left untouched`() {
        val original = checkstyleSchema()
        val catalog = documentationCatalogJson.decodeFromString<DocumentationCatalog>(sampleA)

        val grafted = graftDocumentation(original, catalog)

        val originalType = original.dataClassTypesByFqName[checkstyleFqn] as DataClass
        assertTrue(originalType.metadata.none { it is SchemaDocumentation })
        assertNotSame(originalType, grafted.dataClassTypesByFqName[checkstyleFqn])
    }


    private val checkstyleFqn = DefaultFqName.parse("org.gradle.api.plugins.checkstyle.CheckstyleDefinition")
    private val libraryDependenciesFqn = DefaultFqName.parse("org.gradle.api.plugins.java.LibraryDependencies")

    private val sampleC = """
        {
          "types": {
            "org.gradle.api.plugins.java.LibraryDependencies": {
              "functions": {
                "copyTo(org.gradle.api.plugins.java.LibraryDependencies)": {
                  "documentation": "Copy all dependencies and constraints from this collector to the target.",
                  "parameters": {
                    "target": "Destination receiving the copied dependencies."
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    private val severityFqn = DefaultFqName.parse("org.example.demo.Severity")

    private val javaLibraryModelFqn = DefaultFqName.parse("org.gradle.api.plugins.java.JavaLibraryModel")

    private val sampleM = """
        {
          "types": {
            "org.gradle.api.plugins.java.JavaLibraryModel": {
              "properties": {
                "testReports": "Configures the destinations for test reports produced by this library."
              }
            }
          }
        }
    """.trimIndent()

    private val sampleJ = """
        {
          "enums": {
            "org.example.demo.Severity": {
              "documentation": "Severity level for a quality finding.",
              "entries": {
                "INFO": "Informational; not a violation.",
                "WARN": "A potential issue.",
                "ERROR": "A violation that fails the build."
              }
            }
          }
        }
    """.trimIndent()

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

    private fun checkstyleSchema(): AnalysisSchema {
        val ignoreFailures = property("ignoreFailures", DataTypeInternal.DefaultBooleanDataType)
        val configFile = property("configFile", DataTypeInternal.DefaultStringDataType)
        val checkstyle = dataClass(
            checkstyleFqn,
            "org.gradle.api.plugins.checkstyle.CheckstyleDefinition",
            properties = listOf(ignoreFailures, configFile)
        )
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return schemaWith(
            topLevelReceiver = placeholder,
            types = mapOf(Pair(checkstyleFqn, checkstyle), Pair(placeholder.name, placeholder))
        )
    }

    private fun javaLibraryModelSchema(): AnalysisSchema {
        val testReportsFqn = DefaultFqName.parse("org.gradle.api.plugins.java.JavaLibraryModel.TestReports")
        val testReports = dataClass(testReportsFqn, "org.gradle.api.plugins.java.JavaLibraryModel\$TestReports")
        val testReportsProperty = DefaultDataProperty(
            name = "testReports",
            valueType = DataTypeRefInternal.DefaultName(testReportsFqn),
            mode = DefaultDataProperty.DefaultPropertyMode.DefaultReadOnly,
            hasDefaultValue = false
        )
        val configureFunction = DefaultDataMemberFunction(
            receiver = DataTypeRefInternal.DefaultName(javaLibraryModelFqn),
            simpleName = "testReports",
            parameters = emptyList(),
            isDirectAccessOnly = false,
            semantics = FunctionSemanticsInternal.DefaultAccessAndConfigure(
                accessor = ConfigureAccessorInternal.DefaultProperty(testReportsProperty),
                returnType = FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit,
                configuredType = DataTypeRefInternal.DefaultName(testReportsFqn),
                configureBlockRequirement = FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
            ),
            metadata = listOf(
                SchemaItemMetadataInternal.SchemaMemberOriginInternal.DefaultConfigureFromGetterOrigin(
                    javaClassName = "org.gradle.api.plugins.java.JavaLibraryModel",
                    memberName = "getTestReports"
                )
            )
        )
        val javaLibraryModel = dataClass(
            javaLibraryModelFqn,
            "org.gradle.api.plugins.java.JavaLibraryModel",
            properties = listOf(testReportsProperty),
            memberFunctions = listOf(configureFunction)
        )
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return schemaWith(
            topLevelReceiver = placeholder,
            types = mapOf(
                Pair(javaLibraryModelFqn, javaLibraryModel),
                Pair(testReportsFqn, testReports),
                Pair(placeholder.name, placeholder)
            )
        )
    }

    private fun severityEnumSchema(): AnalysisSchema {
        val severity = DefaultEnumClass(
            name = severityFqn,
            javaTypeName = "org.example.demo.Severity",
            entryNames = listOf("INFO", "WARN", "ERROR"),
            metadata = emptyList()
        )
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return schemaWith(
            topLevelReceiver = placeholder,
            types = mapOf(Pair(severityFqn, severity), Pair(placeholder.name, placeholder))
        )
    }

    private fun libraryDependenciesSchema(): AnalysisSchema {
        val libraryDependencies = dataClass(
            libraryDependenciesFqn,
            "org.gradle.api.plugins.java.LibraryDependencies"
        )
        val targetParam = DefaultDataParameter(
            name = "target",
            type = DataTypeRefInternal.DefaultName(libraryDependenciesFqn),
            isDefault = false,
            semantics = ParameterSemanticsInternal.DefaultUnknown
        )
        val copyTo = DefaultDataMemberFunction(
            receiver = DataTypeRefInternal.DefaultName(libraryDependenciesFqn),
            simpleName = "copyTo",
            parameters = listOf(targetParam),
            isDirectAccessOnly = false,
            semantics = FunctionSemanticsInternal.DefaultPure(DataTypeRefInternal.DefaultType(DataTypeInternal.DefaultUnitType))
        )
        val withFunction = libraryDependencies.copy(memberFunctions = listOf(copyTo))
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return schemaWith(
            topLevelReceiver = placeholder,
            types = mapOf(Pair(libraryDependenciesFqn, withFunction), Pair(placeholder.name, placeholder))
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
        properties: List<DataProperty> = emptyList(),
        memberFunctions: List<SchemaMemberFunction> = emptyList(),
        metadata: List<SchemaItemMetadata> = emptyList()
    ): DefaultDataClass = DefaultDataClass(
        name = fqn,
        javaTypeName = javaTypeName,
        javaTypeArgumentTypeNames = emptyList(),
        supertypes = emptySet(),
        properties = properties,
        memberFunctions = memberFunctions,
        constructors = emptyList(),
        metadata = metadata
    )

    private fun schemaWith(
        topLevelReceiver: DataClass,
        types: Map<FqName, DataType.ClassDataType>,
        signatures: Map<FqName, DataType.ParameterizedTypeSignature> = emptyMap(),
        instantiations: Map<FqName, Map<List<DataType.ParameterizedTypeInstance.TypeArgument>, DataType.ClassDataType>> = emptyMap()
    ): AnalysisSchema = DefaultAnalysisSchema(
        topLevelReceiverType = topLevelReceiver,
        dataClassTypesByFqName = types,
        genericSignaturesByFqName = signatures,
        genericInstantiationsByFqName = instantiations,
        externalFunctionsByFqName = emptyMap(),
        infixFunctionsByFqName = emptyMap(),
        externalObjectsByFqName = emptyMap(),
        defaultImports = emptySet(),
        assignmentAugmentationsByTypeName = emptyMap()
    )
}
