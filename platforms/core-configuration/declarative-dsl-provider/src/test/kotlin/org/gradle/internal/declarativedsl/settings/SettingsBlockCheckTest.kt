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

import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.checks.DocumentCheckFailureReason
import org.gradle.internal.declarativedsl.dom.resolvedDocument
import org.gradle.internal.declarativedsl.dom.toDocument
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.plus
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.plugins.PluginsTopLevelReceiver
import org.gradle.internal.declarativedsl.plugins.isTopLevelPluginsBlock
import org.gradle.internal.declarativedsl.project.gradleDslGeneralSchemaComponent
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
        assertEquals(listOf(2, 4), result.map { it.location.sourceData.lineRange.start })
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
        assertEquals(listOf(2, 4), result.map { it.location.sourceData.lineRange.start })
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
        val languageModel = parse(code)
        val trace = tracingCodeResolver(analysisStatementFilter)
            .apply { resolve(analysisSchema, languageModel.imports, languageModel.topLevelBlock) }
            .trace
        val document = resolvedDocument(analysisSchema, trace, languageModel.toDocument())
        return documentChecks.flatMap { it.detectFailures(document) }
    }

    private
    val pluginManagementSchema = pluginManagementEvaluationSchema()

    private
    val pluginsSchema = buildEvaluationSchema(PluginsTopLevelReceiver::class, gradleDslGeneralSchemaComponent() + SettingsBlocksCheck, isTopLevelPluginsBlock)

    private
    fun parse(source: String): LanguageTreeResult {
        val (tree, code, codeOffset) = org.gradle.internal.declarativedsl.parsing.parse(source)
        return DefaultLanguageTreeBuilder().build(tree, code, codeOffset, SourceIdentifier("test"))
    }
}
