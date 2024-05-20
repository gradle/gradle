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
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.conventions.AdditionRecordConvention
import org.gradle.internal.declarativedsl.conventions.AdditionRecordConventionReceiver
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConvention
import org.gradle.internal.declarativedsl.conventions.AssignmentRecordConventionReceiver
import org.gradle.internal.declarativedsl.conventions.findSoftwareType
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
        val resultHolder = ResultHolder(resolutionResult)

        val softwareTypes = softwareTypeRegistry.softwareTypeImplementations.associateBy { it.softwareType }
        val referencedSoftwareTypes = resolutionResult.nestedObjectAccess
            .filter { isSoftwareTypeAccessor(it.dataObject.accessor) }
            .mapNotNull { softwareTypes[it.dataObject.function.simpleName] }

        val topLevelReceiver = ObjectOrigin.ImplicitThisReceiver(resultHolder.result.topLevelReceiver, true)
        referencedSoftwareTypes.forEach { softwareTypeImplementation ->
            applyAdditionConventions(softwareTypeImplementation, topLevelReceiver, resultHolder)
            applyAssignmentConventions(softwareTypeImplementation, topLevelReceiver, resultHolder)
        }

        return resultHolder.result
    }

    private
    data class ResultHolder(var result: ResolutionResult)

    private
    fun applyAssignmentConventions(
        softwareTypeImplementation: SoftwareTypeImplementation<*>,
        topLevelReceiver: ObjectOrigin.ImplicitThisReceiver,
        resultHolder: ResultHolder
    ) {
        softwareTypeImplementation.conventions.filterIsInstance<AssignmentRecordConvention>().forEach { rule ->
            rule.apply(object : AssignmentRecordConventionReceiver {
                override fun receive(assignmentRecord: AssignmentRecord) {
                    val remappedAssignmentRecord = remapSoftwareTypeToTopLevelReceiver(assignmentRecord, topLevelReceiver)
                    resultHolder.result = resultHolder.result.copy(conventionAssignments = resultHolder.result.conventionAssignments + remappedAssignmentRecord)
                }
            })
        }
    }

    private
    fun applyAdditionConventions(
        softwareTypeImplementation: SoftwareTypeImplementation<*>,
        topLevelReceiver: ObjectOrigin.ImplicitThisReceiver,
        resultHolder: ResultHolder
    ) {
        softwareTypeImplementation.conventions.filterIsInstance<AdditionRecordConvention>().forEach { rule ->
            rule.apply(object : AdditionRecordConventionReceiver {
                override fun receive(additionRecord: DataAdditionRecord) {
                    val remappedAdditionRecord = remapSoftwareTypeToTopLevelReceiver(additionRecord, topLevelReceiver)
                    resultHolder.result = resultHolder.result.copy(conventionAdditions = resultHolder.result.conventionAdditions + remappedAdditionRecord)
                }
            })
        }
    }

    private
    fun remapSoftwareTypeToTopLevelReceiver(assignmentRecord: AssignmentRecord, topLevelReceiver: ObjectOrigin.ImplicitThisReceiver): AssignmentRecord {
        val softwareTypeReceiver = findSoftwareType(assignmentRecord.lhs.receiverObject)
        val lhs = assignmentRecord.lhs.copy(receiverObject = remapAndCopyReceiverHierarchy(assignmentRecord.lhs.receiverObject, softwareTypeReceiver, topLevelReceiver))
        return assignmentRecord.copy(lhs = lhs)
    }

    private
    fun remapSoftwareTypeToTopLevelReceiver(additionRecord: DataAdditionRecord, topLevelReceiver: ObjectOrigin.ImplicitThisReceiver): DataAdditionRecord {
        val softwareTypeReceiver = findSoftwareType(additionRecord.container)
        val remappedContainer = remapAndCopyReceiverHierarchy(additionRecord.container, softwareTypeReceiver, topLevelReceiver)
        val remappedDataObject = remapAndCopyReceiverHierarchy(additionRecord.dataObject, softwareTypeReceiver, topLevelReceiver)
        return additionRecord.copy(container = remappedContainer, dataObject = remappedDataObject)
    }

    private
    fun isSoftwareTypeAccessor(accessor: ConfigureAccessor): Boolean {
        return accessor is ConfigureAccessorInternal.DefaultCustom && accessor.customAccessorIdentifier.startsWith("softwareType:")
    }

    private
    fun remapAndCopyReceiverHierarchy(receiver: ObjectOrigin, softwareTypeReceiver: ObjectOrigin.AccessAndConfigureReceiver, topLevelReceiver: ObjectOrigin.ImplicitThisReceiver): ObjectOrigin {
        return when (receiver) {
            is ObjectOrigin.ImplicitThisReceiver -> {
                receiver.copy(resolvedTo = remapAndCopyReceiverHierarchy(receiver.resolvedTo, softwareTypeReceiver, topLevelReceiver) as ObjectOrigin.ReceiverOrigin)
            }

            is ObjectOrigin.AccessAndConfigureReceiver -> {
                if (receiver == softwareTypeReceiver)
                    receiver.copy(receiver = topLevelReceiver)
                else
                    receiver.copy(receiver = remapAndCopyReceiverHierarchy(receiver.receiver, softwareTypeReceiver, topLevelReceiver))
            }

            is ObjectOrigin.NewObjectFromMemberFunction -> {
                val remappedBindingValues = receiver.parameterBindings.bindingMap.mapValues { (_, value) ->
                    remapAndCopyReceiverHierarchy(value, softwareTypeReceiver, topLevelReceiver)
                }
                val remappedBindings = receiver.parameterBindings.copy(bindingMap = remappedBindingValues)
                receiver.copy(
                    receiver = remapAndCopyReceiverHierarchy(receiver.receiver, softwareTypeReceiver, topLevelReceiver),
                    parameterBindings = remappedBindings
                )
            }

            is ObjectOrigin.PropertyReference -> {
                receiver.copy(receiver = remapAndCopyReceiverHierarchy(receiver.receiver, softwareTypeReceiver, topLevelReceiver))
            }

            is ObjectOrigin.CustomConfigureAccessor -> {
                receiver.copy(receiver = remapAndCopyReceiverHierarchy(receiver.receiver, softwareTypeReceiver, topLevelReceiver))
            }

            is ObjectOrigin.PropertyDefaultValue -> {
                receiver.copy(receiver = remapAndCopyReceiverHierarchy(receiver.receiver, softwareTypeReceiver, topLevelReceiver))
            }

            is ObjectOrigin.ConstantOrigin -> receiver

            is ObjectOrigin.NullObjectOrigin -> receiver

            else -> {
                error("Could not copy receiver hierarchy for $receiver")
            }
        }
    }
}
