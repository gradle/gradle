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

import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.internal.declarativedsl.analysis.AssignmentGenerationId
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeReflectionToObjectConverter
import org.gradle.internal.declarativedsl.mappingToJvm.ReflectionToObjectConverter
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver


internal
class InterpretationSequence(
    val steps: Iterable<InterpretationSequenceStep<*>>
)


internal
interface InterpretationSequenceStep<R : Any> {
    val stepIdentifier: String
    val assignmentGeneration: AssignmentGenerationId
    fun evaluationSchemaForStep(): EvaluationSchema
    fun getTopLevelReceiverFromTarget(target: Any): R
    fun whenEvaluated(resultReceiver: R)
    fun whenResolved(resolutionResult: ResolutionResult) = Unit
    fun getReflectionToObjectConverter(
        externalObjectsMap: Map<ExternalObjectProviderKey, Any>,
        topLevelObject: Any,
        functionResolver: RuntimeFunctionResolver,
        propertyResolver: RuntimePropertyResolver,
        customAccessors: RuntimeCustomAccessors
    ): ReflectionToObjectConverter = DeclarativeReflectionToObjectConverter(
        externalObjectsMap,
        topLevelObject,
        functionResolver,
        propertyResolver,
        customAccessors
    )
}


/**
 * Implements a straightforward interpretation sequence step that uses the target as the top-level receiver.
 * and produces an evaluation schema with [buildEvaluationSchema] immediately before the step runs.
 */
internal
class SimpleInterpretationSequenceStep(
    override val stepIdentifier: String,
    override val assignmentGeneration: AssignmentGenerationId = AssignmentGenerationId.PROPERTY_ASSIGNMENT,
    private val buildEvaluationSchema: () -> EvaluationSchema
) : InterpretationSequenceStep<Any> {
    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationSchema()
    override fun getTopLevelReceiverFromTarget(target: Any): Any = target
    override fun whenEvaluated(resultReceiver: Any) = Unit
}
