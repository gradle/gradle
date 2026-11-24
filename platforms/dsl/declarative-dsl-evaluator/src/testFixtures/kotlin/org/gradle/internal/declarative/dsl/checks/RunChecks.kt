/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarative.dsl.checks

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.analysis.AnalyzedStatementUtils
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheck
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentLowLevelResolutionCheck
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse

fun EvaluationSchema.runChecks(code: String, documentChecks: List<DocumentCheck>): List<DocumentCheckFailure> {
    val languageModel = DefaultLanguageTreeBuilder().build(parse(code), SourceIdentifier("test"))
    val trace = tracingCodeResolver(DefaultOperationGenerationId.Companion.finalEvaluation, analysisStatementFilter)
        .apply { resolve(analysisSchema, languageModel.imports, languageModel.topLevelBlock) }
        .trace
    val document = languageModel.toDocument()
    val resolution = resolutionContainer(analysisSchema, trace, document)
    val isAnalyzedDocumentNode = AnalyzedStatementUtils.produceIsAnalyzedNodeContainer(document.languageTreeMappingContainer, languageModel.topLevelBlock, analysisStatementFilter)
    return documentChecks.flatMap {
        it.detectFailures(DocumentWithResolution(document, resolution), isAnalyzedDocumentNode) +
            if (it is DocumentLowLevelResolutionCheck) it.detectFailuresInLowLevelResolution(document, document.languageTreeMappingContainer, trace) else emptyList()
    }
}
