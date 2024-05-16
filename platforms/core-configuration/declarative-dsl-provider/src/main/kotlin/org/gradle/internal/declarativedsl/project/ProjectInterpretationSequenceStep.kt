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

package org.gradle.internal.declarativedsl.project

import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConvention
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConventionReceiver
import org.gradle.internal.declarativedsl.conventions.getSoftwareType
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


class ProjectInterpretationSequenceStep(
    override val stepIdentifier: String = "project",
    private val softwareTypeRegistry: SoftwareTypeRegistry,
    private val buildEvaluationSchema: () -> EvaluationSchema
) : InterpretationSequenceStep<Any> {
    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationSchema()

    override fun getTopLevelReceiverFromTarget(target: Any): Any = target

    override fun whenEvaluated(resultReceiver: Any) = Unit

    override fun whenResolved(resolutionResult: ResolutionResult) {
        if (resolutionResult.topLevelReceiver.originElement is Block) {
            val softwareTypes = softwareTypeRegistry.softwareTypeImplementations.associateBy { it.softwareType }
            val referencedSoftwareTypes = (resolutionResult.topLevelReceiver.originElement as Block).content
                .filterIsInstance<FunctionCall>()
                .filter { softwareTypes.containsKey(it.name) }
                .mapNotNull { softwareTypes[it.name] }
            referencedSoftwareTypes.forEach {
                val topLevelReceiver = ObjectOrigin.ImplicitThisReceiver(resolutionResult.topLevelReceiver, true)

                it.conventions.filterIsInstance<AssignmentRecordConvention>().forEach { rule ->
                    rule.apply(object : AssignmentRecordConventionReceiver {
                        override fun receive(assignmentRecord: AssignmentRecord) {
                            val softwareTypeReceiver = getSoftwareType(assignmentRecord.lhs.receiverObject)
                            softwareTypeReceiver.receiver = topLevelReceiver
                            resolutionResult.conventionAssignments += assignmentRecord
                        }
                    })
                }
            }
        }
    }
}
