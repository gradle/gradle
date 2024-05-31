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
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.InterpretationSequenceStepWithConversion


class DefaultInterpretationSequence(
    override val steps: Iterable<InterpretationSequenceStep>
) : InterpretationSequence


class DefaultStepIdentifier(override val key: String) : InterpretationSequenceStep.StepIdentifier


class SimpleInterpretationSequenceStep(
    override val stepIdentifier: InterpretationSequenceStep.StepIdentifier,
    override val features: Set<InterpretationStepFeature> = emptySet(),
    buildEvaluationAndConversionSchema: () -> EvaluationSchema
) : InterpretationSequenceStep {

    constructor(stepIdentifierString: String, features: Set<InterpretationStepFeature>, buildEvaluationAndConversionSchema: () -> EvaluationSchema) :
        this(DefaultStepIdentifier(stepIdentifierString), features, buildEvaluationAndConversionSchema)

    override val evaluationSchemaForStep: EvaluationSchema by lazy(buildEvaluationAndConversionSchema)
}


/**
 * Implements a straightforward interpretation sequence step that uses the target as the top-level receiver
 * and produces an evaluation schema with [buildEvaluationAndConversionSchema] lazily before the step runs.
 */
internal
class SimpleInterpretationSequenceStepWithConversion private constructor(
    override val stepIdentifier: InterpretationSequenceStep.StepIdentifier,
    override val features: Set<InterpretationStepFeature> = emptySet(),
    buildEvaluationAndConversionSchema: () -> EvaluationAndConversionSchema
) : InterpretationSequenceStepWithConversion<Any> {

    constructor(
        stepIdentifierString: String,
        features: Set<InterpretationStepFeature> = emptySet(),
        buildEvaluationAndConversionSchema: () -> EvaluationAndConversionSchema
    ) : this(DefaultStepIdentifier(stepIdentifierString), features, buildEvaluationAndConversionSchema)

    override val evaluationSchemaForStep: EvaluationAndConversionSchema by lazy(buildEvaluationAndConversionSchema)
    override fun getTopLevelReceiverFromTarget(target: Any): Any = target
    override fun whenEvaluated(resultReceiver: Any) = Unit
}
