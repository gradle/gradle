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

import org.gradle.internal.declarativedsl.analysis.OperationGenerationId
import org.gradle.internal.declarativedsl.features.InterpretationStepFeature


internal
class InterpretationSequence(
    val steps: Iterable<InterpretationSequenceStep>
)


internal
interface InterpretationSequenceStep {
    val stepIdentifier: String
    val assignmentGeneration: OperationGenerationId
    val features: Set<InterpretationStepFeature>
    fun evaluationSchemaForStep(): EvaluationSchema
}


internal
interface InterpretationSequenceStepWithConversion<R : Any> : InterpretationSequenceStep {
    override fun evaluationSchemaForStep(): EvaluationAndConversionSchema
    fun getTopLevelReceiverFromTarget(target: Any): R
    fun whenEvaluated(resultReceiver: R)
}


internal
class SimpleInterpretationSequenceStep(
    override val stepIdentifier: String,
    override val assignmentGeneration: OperationGenerationId = OperationGenerationId.PROPERTY_ASSIGNMENT,
    override val features: Set<InterpretationStepFeature> = emptySet(),
    private val buildEvaluationAndConversionSchema: () -> EvaluationSchema
) : InterpretationSequenceStep {
    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationAndConversionSchema()
}


/**
 * Implements a straightforward interpretation sequence step that uses the target as the top-level receiver
 * and produces an evaluation schema with [buildEvaluationSchema] immediately before the step runs.
 */
internal
class SimpleInterpretationSequenceStepWithConversion(
    override val stepIdentifier: String,
    override val assignmentGeneration: OperationGenerationId = OperationGenerationId.PROPERTY_ASSIGNMENT,
    override val features: Set<InterpretationStepFeature> = emptySet(),
    private val buildEvaluationAndConversionSchema: () -> EvaluationAndConversionSchema
) : InterpretationSequenceStepWithConversion<Any> {
    override fun evaluationSchemaForStep(): EvaluationAndConversionSchema = buildEvaluationAndConversionSchema()
    override fun getTopLevelReceiverFromTarget(target: Any): Any = target
    override fun whenEvaluated(resultReceiver: Any) = Unit
}
