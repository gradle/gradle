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
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.transformation.OriginReplacement.replaceReceivers
import org.gradle.internal.declarativedsl.conventions.AdditionRecordConvention
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConvention
import org.gradle.internal.declarativedsl.conventions.isConventionsCall
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
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

        val softwareTypes = softwareTypeRegistry.softwareTypeImplementations.associateBy { it.softwareType }
        val referencedSoftwareTypes = resolutionResult.nestedObjectAccess
            .filter { isSoftwareTypeAccessor(it.dataObject.accessor) }
            .mapNotNull { softwareTypes[it.dataObject.function.simpleName] }

        val conventionAssignments: List<AssignmentRecord> = applyAssignmentConventions(referencedSoftwareTypes, resolutionResult.topLevelReceiver)
        val conventionAdditions: List<DataAdditionRecord> = applyAdditionConventions(referencedSoftwareTypes, resolutionResult.topLevelReceiver)

        return resolutionResult.copy(
            conventionAssignments = resolutionResult.conventionAssignments + conventionAssignments,
            conventionAdditions = resolutionResult.conventionAdditions + conventionAdditions
        )
    }

    private
    fun applyAssignmentConventions(
        referencedSoftwareTypes: List<SoftwareTypeImplementation<*>>,
        topLevelReceiver: ObjectOrigin.TopLevelReceiver
    ): List<AssignmentRecord> = buildList {
        referencedSoftwareTypes.forEach { softwareType ->
            softwareType.conventions.filterIsInstance<AssignmentRecordConvention>().forEach { convention ->
                convention.apply { assignmentRecord ->
                    add(
                        assignmentRecord.copy(
                            lhs = assignmentRecord.lhs.copy(
                                receiverObject = replaceReceivers(assignmentRecord.lhs.receiverObject, ::isConventionsCall, topLevelReceiver)
                            ),
                            rhs = replaceReceivers(assignmentRecord.rhs, ::isConventionsCall, topLevelReceiver)
                        )
                    )
                }
            }
        }
    }

    private
    fun applyAdditionConventions(
        referencedSoftwareTypes: List<SoftwareTypeImplementation<*>>,
        topLevelReceiver: ObjectOrigin.TopLevelReceiver
    ): List<DataAdditionRecord> = buildList {
        referencedSoftwareTypes.forEach { softwareType ->
            softwareType.conventions.filterIsInstance<AdditionRecordConvention>().forEach { convention ->
                convention.apply { additionRecord ->
                    add(
                        DataAdditionRecord(
                            container = replaceReceivers(additionRecord.container, ::isConventionsCall, topLevelReceiver),
                            dataObject = replaceReceivers(additionRecord.dataObject, ::isConventionsCall, topLevelReceiver)
                        )
                    )
                }
            }
        }
    }

    private
    fun isSoftwareTypeAccessor(accessor: ConfigureAccessor): Boolean {
        return accessor is ConfigureAccessorInternal.DefaultCustom && accessor.customAccessorIdentifier.startsWith("softwareType:")
    }
}
