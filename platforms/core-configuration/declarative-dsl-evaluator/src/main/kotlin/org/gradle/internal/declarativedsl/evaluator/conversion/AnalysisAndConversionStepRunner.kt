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

package org.gradle.internal.declarativedsl.evaluator.conversion

import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepResult
import org.gradle.internal.declarativedsl.evaluator.runner.InterpretationSequenceStepRunner
import org.gradle.internal.declarativedsl.evaluator.runner.StepContext
import org.gradle.internal.declarativedsl.evaluator.runner.StepResult
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.CompositePropertyResolver
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeReflectionToObjectConverter
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection
import org.gradle.internal.declarativedsl.objectGraph.ReflectionContext
import org.gradle.internal.declarativedsl.objectGraph.reflect


data class ConversionStepContext(
    val targetObject: Any,
    val analysisStepContext: AnalysisStepContext
) : StepContext


sealed interface ConversionStepResult : StepResult {
    data class ConversionSucceeded(
        val analysisResult: AnalysisStepResult
    ) : ConversionStepResult

    data class ConversionNotApplicable(
        val analysisResult: AnalysisStepResult
    ) : ConversionStepResult

    data class AnalysisFailed(
        val partialAnalysisResult: AnalysisStepResult,
    ) : ConversionStepResult

    data object CannotRunStep : ConversionStepResult
}


class AnalysisAndConversionStepRunner(
    private val analysisStepRunner: InterpretationSequenceStepRunner<AnalysisStepContext, AnalysisStepResult>
) : InterpretationSequenceStepRunner<ConversionStepContext, ConversionStepResult> {

    override fun runInterpretationSequenceStep(
        scriptIdentifier: String,
        scriptSource: String,
        step: InterpretationSequenceStep,
        stepContext: ConversionStepContext
    ): EvaluationResult<ConversionStepResult> = when (val analysisResult = analysisStepRunner.runInterpretationSequenceStep(scriptIdentifier, scriptSource, step, stepContext.analysisStepContext)) {
        is EvaluationResult.NotEvaluated ->
            EvaluationResult.NotEvaluated(analysisResult.stageFailures, ConversionStepResult.AnalysisFailed(analysisResult.partialStepResult))

        is EvaluationResult.Evaluated ->
            if (step is InterpretationSequenceStepWithConversion<*>) {
                val evaluationSchema = step.evaluationSchemaForStep
                val context = ReflectionContext(
                    SchemaTypeRefContext(evaluationSchema.analysisSchema),
                    analysisResult.stepResult.resolutionResult,
                    analysisResult.stepResult.assignmentTrace
                )
                val topLevelObjectReflection = reflect(analysisResult.stepResult.resolutionResult.topLevelReceiver, context)
                applyReflectionToJvmObjectConversion(evaluationSchema, step, stepContext.targetObject, topLevelObjectReflection)
                EvaluationResult.Evaluated(ConversionStepResult.ConversionSucceeded(analysisResult.stepResult))
            } else EvaluationResult.Evaluated(ConversionStepResult.ConversionNotApplicable(analysisResult.stepResult))
    }

    private
    fun <R : Any> applyReflectionToJvmObjectConversion(
        evaluationSchema: EvaluationAndConversionSchema,
        step: InterpretationSequenceStepWithConversion<R>,
        target: Any,
        topLevelObjectReflection: ObjectReflection
    ) {
        val conversionSchema = evaluationSchema.conversionSchemaForScriptTarget(target)
        val propertyResolver = CompositePropertyResolver(conversionSchema.runtimePropertyResolvers)
        val functionResolver = CompositeFunctionResolver(conversionSchema.runtimeFunctionResolvers)
        val customAccessors = CompositeCustomAccessors(conversionSchema.runtimeCustomAccessors)

        val topLevelReceiver = step.getTopLevelReceiverFromTarget(target)
        val converter = DeclarativeReflectionToObjectConverter(
            emptyMap(), topLevelReceiver, functionResolver, propertyResolver, customAccessors
        )
        converter.apply(topLevelObjectReflection)

        step.whenEvaluated(target, topLevelReceiver)
    }
}
