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

package org.gradle.internal.declarativedsl.evaluator.conventions

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.internal.declarativedsl.conventions.softwareTypeRegistryBasedConventionRepository
import org.gradle.internal.declarativedsl.evaluator.DeclarativeDslNotEvaluatedException
import org.gradle.internal.declarativedsl.evaluator.conversion.AnalysisAndConversionStepRunner
import org.gradle.internal.declarativedsl.evaluator.conversion.ConversionStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AbstractAnalysisStepRunner
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.Evaluated
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.evaluator.runner.ParseAndResolveResult
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.project.projectInterpretationSequenceStep
import org.gradle.plugin.software.internal.ConventionHandler
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


/**
 * A {@link ConventionHandler} for applying declarative conventions.
 */
class SoftwareTypeConventionHandler(softwareTypeRegistry: SoftwareTypeRegistry) : ConventionHandler {
    private
    val step = projectInterpretationSequenceStep(softwareTypeRegistry)
    private
    val conventionRepository = softwareTypeRegistryBasedConventionRepository(softwareTypeRegistry)

    override fun apply(target: Any, softwareTypeName: String) {
        val analysisStepRunner = ApplyConventionsOnlyAnalysisStepRunner()
        val analysisStepContext = AnalysisStepContext(
            emptySet(),
            setOf(
                ConventionApplicationHandler { listOf(conventionRepository.findConventions(softwareTypeName)).requireNoNulls() }
            )
        )

        val result = AnalysisAndConversionStepRunner(analysisStepRunner)
            .runInterpretationSequenceStep(
                "<none>",
                "",
                step,
                ConversionStepContext(target, analysisStepContext)
            )

        when (result) {
            is Evaluated -> Unit
            is NotEvaluated -> {
                // TODO: What kind of errors can actually come out of the conversion step?  Should we have a better exception type since we're
                //  unlikely to have analysis errors?
                throw DeclarativeDslNotEvaluatedException("", result.stageFailures)
            }
        }
    }
}


private
class ApplyConventionsOnlyAnalysisStepRunner : AbstractAnalysisStepRunner() {
    override fun parseAndResolve(evaluationSchema: EvaluationSchema, scriptIdentifier: String, scriptSource: String): ParseAndResolveResult {
        // Create a synthetic top level receiver
        val topLevelBlock = Block(emptyList(), emptySourceData())
        val languageTreeResult = LanguageTreeResult(emptyList(), topLevelBlock, emptyList(), emptyList())
        val topLevelReceiver = ObjectOrigin.TopLevelReceiver(evaluationSchema.analysisSchema.topLevelReceiverType, topLevelBlock)

        return ParseAndResolveResult(languageTreeResult, emptyResolutionResultForReceiver(topLevelReceiver), emptyResolutionTrace(), emptyList())
    }
}


private
fun emptyResolutionTrace() = object : ResolutionTrace {
    override fun assignmentResolution(assignment: Assignment): ResolutionTrace.ResolutionOrErrors<AssignmentRecord> {
        throw UnsupportedOperationException()
    }

    override fun expressionResolution(expr: Expr): ResolutionTrace.ResolutionOrErrors<ObjectOrigin> {
        throw UnsupportedOperationException()
    }
}


private
fun emptyResolutionResultForReceiver(receiver: ObjectOrigin.TopLevelReceiver) = ResolutionResult(
    receiver,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList()
)


private
fun emptySourceData() = object : SourceData {
    override val sourceIdentifier: SourceIdentifier
        get() = SourceIdentifier("<none>")
    override val indexRange: IntRange
        get() = IntRange.EMPTY
    override val lineRange: IntRange
        get() = IntRange.EMPTY
    override val startColumn: Int
        get() = -1
    override val endColumn: Int
        get() = -1

    override fun text(): String = "<none>"
}
