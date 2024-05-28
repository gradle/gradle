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
import org.gradle.internal.declarativedsl.analysis.OperationGenerationId
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.transformation.OriginReplacement.replaceReceivers
import org.gradle.internal.declarativedsl.conventions.AdditionRecordConvention
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConvention
import org.gradle.internal.declarativedsl.conventions.NestedObjectAccessConvention
import org.gradle.internal.declarativedsl.conventions.isConventionsCall
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


/**
 * A step in the interpretation sequence that processes the project build file and applies Software Type conventions
 * configured in the Settings DSL.
 */
class ProjectInterpretationSequenceStep(
    override val stepIdentifier: String = "project",
    override val assignmentGeneration: OperationGenerationId = OperationGenerationId.PROPERTY_ASSIGNMENT,
    private val softwareTypeRegistry: SoftwareTypeRegistry,
    private val buildEvaluationSchema: () -> EvaluationSchema
) : InterpretationSequenceStep<Any> {
    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationSchema()

    override fun getTopLevelReceiverFromTarget(target: Any): Any = target

    override fun whenEvaluated(resultReceiver: Any) = Unit

    override fun processResolutionResult(resolutionResult: ResolutionResult): ResolutionResult {

        // Find all the software types that are referenced in the build file
        val softwareTypes = softwareTypeRegistry.softwareTypeImplementations.associateBy { it.softwareType }
        val referencedSoftwareTypes = resolutionResult.nestedObjectAccess
            .filter { isSoftwareTypeAccessor(it.dataObject.accessor) }
            .mapNotNull { softwareTypes[it.dataObject.function.simpleName] }

        // For the referenced software types, add their conventions as operations mapped onto the top-level receiver
        val conventionAssignments = applyAssignmentConventions(referencedSoftwareTypes, resolutionResult.topLevelReceiver)
        val conventionAdditions = applyAdditionConventions(referencedSoftwareTypes, resolutionResult.topLevelReceiver)
        val conventionNestedObjectAccess = applyNestedObjectAccessConvention(referencedSoftwareTypes, resolutionResult.topLevelReceiver)

        // Return a resolution result with the convention operations added
        return resolutionResult.copy(
            conventionAssignments = resolutionResult.conventionAssignments + conventionAssignments,
            conventionAdditions = resolutionResult.conventionAdditions + conventionAdditions,
            conventionNestedObjectAccess = resolutionResult.conventionNestedObjectAccess + conventionNestedObjectAccess
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
    fun applyNestedObjectAccessConvention(
        referencedSoftwareTypes: List<SoftwareTypeImplementation<*>>,
        topLevelReceiver: ObjectOrigin.TopLevelReceiver
    ): List<NestedObjectAccessRecord> = buildList {
        referencedSoftwareTypes.forEach { softwareType ->
            softwareType.conventions.filterIsInstance<NestedObjectAccessConvention>().forEach { convention ->
                convention.apply { additionRecord ->
                    add(
                        NestedObjectAccessRecord(
                            container = replaceReceivers(additionRecord.container, ::isConventionsCall, topLevelReceiver),
                            // Expect that the type remains the same: the only thing that will be mapped to a different type is the `conventions { ... }`
                            dataObject = replaceReceivers(additionRecord.dataObject, ::isConventionsCall, topLevelReceiver) as ObjectOrigin.AccessAndConfigureReceiver
                        )
                    )
                }
            }
        }
    }


    private
    fun isSoftwareTypeAccessor(accessor: ConfigureAccessor): Boolean {
        return accessor is ConfigureAccessor.Custom && accessor.customAccessorIdentifier.startsWith("softwareType:")
    }
}
