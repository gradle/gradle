/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.evaluationSchema

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.InterpretationSequenceStepWithConversion


class DefaultInterpretationSequence(
    override val steps: Iterable<InterpretationSequenceStep>
) : InterpretationSequence


class SimpleInterpretationSequenceStep(
    override val stepIdentifier: String,
    override val assignmentGeneration: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation,
    override val features: Set<InterpretationStepFeature> = emptySet(),
    buildEvaluationAndConversionSchema: () -> EvaluationSchema
) : InterpretationSequenceStep {
    override val evaluationSchemaForStep: EvaluationSchema by lazy(buildEvaluationAndConversionSchema)
}


/**
 * Implements a straightforward interpretation sequence step that uses the target as the top-level receiver
 * and produces an evaluation schema with [buildEvaluationAndConversionSchema] lazily before the step runs.
 */
internal
class SimpleInterpretationSequenceStepWithConversion(
    override val stepIdentifier: String,
    override val assignmentGeneration: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation,
    override val features: Set<InterpretationStepFeature> = emptySet(),
    buildEvaluationAndConversionSchema: () -> EvaluationAndConversionSchema
) : InterpretationSequenceStepWithConversion<Any> {
    override val evaluationSchemaForStep: EvaluationAndConversionSchema by lazy(buildEvaluationAndConversionSchema)
    override fun getTopLevelReceiverFromTarget(target: Any): Any = target
    override fun whenEvaluated(resultReceiver: Any) = Unit
}
