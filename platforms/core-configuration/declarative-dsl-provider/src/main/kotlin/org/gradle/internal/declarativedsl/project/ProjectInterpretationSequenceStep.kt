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

import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.internal.declarativedsl.analysis.AssignmentGenerationId
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConvention
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConventionReceiver
import org.gradle.internal.declarativedsl.conventions.getSoftwareType
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


class ProjectInterpretationSequenceStep(
    override val stepIdentifier: String = "project",
    override val assignmentGeneration: AssignmentGenerationId = AssignmentGenerationId.PROPERTY_ASSIGNMENT,
    private val softwareTypeRegistry: SoftwareTypeRegistry,
    private val buildEvaluationSchema: () -> EvaluationSchema
) : InterpretationSequenceStep<Any> {
    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationSchema()

    override fun getTopLevelReceiverFromTarget(target: Any): Any = target

    override fun whenEvaluated(resultReceiver: Any) = Unit

    override fun processResolutionResult(resolutionResult: ResolutionResult): ResolutionResult {
        var newResolutionResult = resolutionResult

        if (newResolutionResult.topLevelReceiver.originElement is Block) {
            val remapped = mutableSetOf<String>()
            val softwareTypes = softwareTypeRegistry.softwareTypeImplementations.associateBy { it.softwareType }
            val referencedSoftwareTypes = resolutionResult.nestedObjectAccess
                .filter { isSoftwareTypeAccessor(it.dataObject.accessor) }
                .mapNotNull { softwareTypes[it.dataObject.function.simpleName] }

            referencedSoftwareTypes.forEach { softwareTypeImplementation ->
                val topLevelReceiver = ObjectOrigin.ImplicitThisReceiver(newResolutionResult.topLevelReceiver, true)
                softwareTypeImplementation.conventions.filterIsInstance<AssignmentRecordConvention>().forEach { rule ->
                    rule.apply(object : AssignmentRecordConventionReceiver {
                        override fun receive(assignmentRecord: AssignmentRecord) {
                            if (!remapped.contains(softwareTypeImplementation.softwareType)) {
                                remapSoftwareTypeToTopLevelReceiver(assignmentRecord, topLevelReceiver)
                                remapped.add(softwareTypeImplementation.softwareType)
                            }
                            newResolutionResult = newResolutionResult.copy(conventionAssignments = newResolutionResult.conventionAssignments + assignmentRecord)
                        }
                    })
                }
            }
        }

        return newResolutionResult
    }

    private
    fun remapSoftwareTypeToTopLevelReceiver(assignmentRecord: AssignmentRecord, topLevelReceiver: ObjectOrigin.ImplicitThisReceiver) {
        val softwareTypeReceiver = getSoftwareType(assignmentRecord.lhs.receiverObject)
        softwareTypeReceiver.receiver = topLevelReceiver
    }

    private
    fun isSoftwareTypeAccessor(accessor: ConfigureAccessor): Boolean {
        return accessor is ConfigureAccessorInternal.DefaultCustom && accessor.customAccessorIdentifier.startsWith("softwareType:")
    }
}
