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

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver


interface EvaluationAndConversionSchema : EvaluationSchema {
    val runtimePropertyResolvers: List<RuntimePropertyResolver>
    val runtimeFunctionResolvers: List<RuntimeFunctionResolver>
    val runtimeCustomAccessors: List<RuntimeCustomAccessors>
}


interface InterpretationSequenceStepWithConversion<R : Any> : InterpretationSequenceStep {
    override val evaluationSchemaForStep: EvaluationAndConversionSchema
    fun getTopLevelReceiverFromTarget(target: Any): R
    fun whenEvaluated(resultReceiver: R)
}
