/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.settings

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailureReason
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse
import org.gradle.internal.declarativedsl.plugins.PluginsTopLevelReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class SettingsBlockCheckTest {
    @Test
    fun `reports all duplicate plugins blocks`() {
        val result = pluginsSchema.runChecks(
            """
            plugins { }
            plugins { }
            rootProject.name = "foo"
            plugins { }
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals(listOf(2, 4), result.map { it.location.sourceData.lineRange.first })
        assertTrue(result.all { it.reason == DocumentCheckFailureReason.DuplicatePluginsBlock })
    }

    @Test
    fun `reports all duplicate pluginManagement blocks`() {
        val result = pluginManagementSchema.runChecks(
            """
            pluginManagement { }
            pluginManagement { }
            rootProject.name = "foo"
            pluginManagement { }
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals(listOf(2, 4), result.map { it.location.sourceData.lineRange.first })
        assertTrue(result.all { it.reason == DocumentCheckFailureReason.DuplicatePluginManagementBlock })
    }

    @Test
    fun `ignores unresolved pluginsManagement blocks before plugins`() {
        val result = pluginsSchema.runChecks(
            """
            pluginManagement { }
            pluginManagement { }
            plugins { }
            """.trimIndent()
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `reports all order violations for plugins blocks`() {
        val result = pluginsSchema.runChecks(
            """
            foo()
            bar()
            baz()
            pluginManagement { }
            plugins {
                id("foo")
            }
            baq()
            """.trimIndent()
        )

        assertEquals(listOf("foo()", "bar()", "baz()"), result.map { it.location.sourceData.text() })
        assertTrue(result.all { it.reason == DocumentCheckFailureReason.PluginsBlockOrderViolated })
    }

    @Test
    fun `reports all order violations for pluginManagement block`() {
        val result = pluginManagementSchema.runChecks(
            """
            foo()
            bar()
            plugins { id("x") }
            pluginManagement { }
            baq()
            """.trimIndent()
        )

        assertEquals(listOf("foo()", "bar()", "plugins { id(\"x\") }"), result.map { it.location.sourceData.text() })
        assertTrue(result.all { it.reason == DocumentCheckFailureReason.PluginManagementBlockOrderViolated })
    }

    private
    fun EvaluationSchema.runChecks(code: String): List<DocumentCheckFailure> {
        val languageModel = DefaultLanguageTreeBuilder().build(parse(code), SourceIdentifier("test"))
        val trace = tracingCodeResolver(DefaultOperationGenerationId.finalEvaluation, analysisStatementFilter)
            .apply { resolve(analysisSchema, languageModel.imports, languageModel.topLevelBlock) }
            .trace
        val document = languageModel.toDocument()
        val resolution = resolutionContainer(analysisSchema, trace, document)
        return documentChecks.flatMap { it.detectFailures(document, resolution) }
    }

    private
    val documentChecks = listOf(SettingsBlocksCheck)

    private
    val pluginManagementSchema = pluginManagementEvaluationSchema()

    private
    val pluginsSchema = buildEvaluationSchema(
        PluginsTopLevelReceiver::class,
        isTopLevelPluginsBlock,
        schemaComponents = EvaluationSchemaBuilder::gradleDslGeneralSchema
    )
}
