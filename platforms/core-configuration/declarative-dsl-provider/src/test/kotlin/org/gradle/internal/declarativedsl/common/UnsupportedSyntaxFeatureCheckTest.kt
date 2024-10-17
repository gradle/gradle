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

package org.gradle.internal.declarativedsl.common

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.analysis.AnalyzedStatementUtils
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntaxCause
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailureReason.UnsupportedSyntaxInDocument
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse
import org.gradle.internal.declarativedsl.project.ProjectTopLevelReceiver
import org.junit.Assert
import org.junit.Test

class UnsupportedSyntaxFeatureCheckTest {

    @Test
    fun `report local values`() {
        val result = projectSchema.runChecks(
            """
            val a = 1
            """.trimIndent()
        )

        Assert.assertEquals(1, result.size)
        Assert.assertEquals(listOf(1), result.map { it.location.sourceData.lineRange.first })
        Assert.assertTrue(result.all { it.reason == UnsupportedSyntaxInDocument(UnsupportedSyntaxCause.LocalVal) })
    }

    @Test
    fun `report named reference with explicit receiver`() {
        val result = projectSchema.runChecks(
            """
            a = Color.blue
            """.trimIndent()
        )

        Assert.assertEquals(1, result.size)
        Assert.assertEquals(listOf(1), result.map { it.location.sourceData.lineRange.first })
        Assert.assertTrue(result.all { it.reason == UnsupportedSyntaxInDocument(UnsupportedSyntaxCause.NamedReferenceWithExplicitReceiver) })
    }

    private
    fun EvaluationSchema.runChecks(code: String): List<DocumentCheckFailure> {
        val languageModel = DefaultLanguageTreeBuilder().build(parse(code), SourceIdentifier("test"))
        val trace = tracingCodeResolver(DefaultOperationGenerationId.finalEvaluation, analysisStatementFilter)
            .apply { resolve(analysisSchema, languageModel.imports, languageModel.topLevelBlock) }
            .trace
        val document = languageModel.toDocument()
        val resolution = resolutionContainer(analysisSchema, trace, document)
        val isAnalyzedDocumentNode = AnalyzedStatementUtils.produceIsAnalyzedNodeContainer(document.languageTreeMappingContainer, languageModel.topLevelBlock, analysisStatementFilter)
        return documentChecks.flatMap { it.detectFailures(document, resolution, isAnalyzedDocumentNode) }
    }

    private
    val documentChecks = listOf(UnsupportedSyntaxFeatureCheck)

    private
    val projectSchema = buildEvaluationSchema(
        ProjectTopLevelReceiver::class,
        analyzeEverything,
        schemaComponents = EvaluationSchemaBuilder::gradleDslGeneralSchema,
    )
}
