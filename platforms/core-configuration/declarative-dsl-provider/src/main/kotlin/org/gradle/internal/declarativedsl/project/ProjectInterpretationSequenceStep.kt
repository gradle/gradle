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

        val remapped = mutableSetOf<String>()
        val softwareTypes = softwareTypeRegistry.softwareTypeImplementations.associateBy { it.softwareType }
        val referencedSoftwareTypes = resolutionResult.nestedObjectAccess
            .filter { isSoftwareTypeAccessor(it.dataObject.accessor) }
            .mapNotNull { softwareTypes[it.dataObject.function.simpleName] }

        referencedSoftwareTypes.forEach { softwareTypeImplementation ->
            val topLevelReceiver = ObjectOrigin.ImplicitThisReceiver(resultHolder.result.topLevelReceiver, true)
            applyAdditionConventions(softwareTypeImplementation, remapped, topLevelReceiver, resultHolder)
            applyAssignmentConventions(softwareTypeImplementation, remapped, topLevelReceiver, resultHolder)
        }

        return resultHolder.result
    }

    private
    data class ResultHolder(var result: ResolutionResult)

    private
    fun applyAssignmentConventions(
        softwareTypeImplementation: SoftwareTypeImplementation<*>,
        remapped: MutableSet<String>,
        topLevelReceiver: ObjectOrigin.ImplicitThisReceiver,
        resultHolder: ResultHolder
    ) {
        softwareTypeImplementation.conventions.filterIsInstance<AssignmentRecordConvention>().forEach { rule ->
            rule.apply(object : AssignmentRecordConventionReceiver {
                override fun receive(assignmentRecord: AssignmentRecord) {
                    if (!remapped.contains(softwareTypeImplementation.softwareType)) {
                        remapSoftwareTypeToTopLevelReceiver(assignmentRecord, topLevelReceiver)
                        remapped.add(softwareTypeImplementation.softwareType)
                    }
                    resultHolder.result = resultHolder.result.copy(conventionAssignments = resultHolder.result.conventionAssignments + assignmentRecord)
                }
            })
        }
    }

    private
    fun applyAdditionConventions(
        softwareTypeImplementation: SoftwareTypeImplementation<*>,
        remapped: MutableSet<String>,
        topLevelReceiver: ObjectOrigin.ImplicitThisReceiver,
        resultHolder: ResultHolder
    ) {
        softwareTypeImplementation.conventions.filterIsInstance<AdditionRecordConvention>().forEach { rule ->
            rule.apply(object : AdditionRecordConventionReceiver {
                override fun receive(additionRecord: DataAdditionRecord) {
                    if (!remapped.contains(softwareTypeImplementation.softwareType)) {
                        remapSoftwareTypeToTopLevelReceiver(additionRecord, topLevelReceiver)
                        remapped.add(softwareTypeImplementation.softwareType)
                    }
                    resultHolder.result = resultHolder.result.copy(conventionAdditions = resultHolder.result.conventionAdditions + additionRecord)
                }
            })
        }
    }

    private
    fun remapSoftwareTypeToTopLevelReceiver(assignmentRecord: AssignmentRecord, topLevelReceiver: ObjectOrigin.ImplicitThisReceiver) {
        val softwareTypeReceiver = findSoftwareType(assignmentRecord.lhs.receiverObject)
        softwareTypeReceiver.receiver = topLevelReceiver
    }

    private
    fun remapSoftwareTypeToTopLevelReceiver(additionRecord: DataAdditionRecord, topLevelReceiver: ObjectOrigin.ImplicitThisReceiver) {
        val softwareTypeReceiver = findSoftwareType(additionRecord.container)
        softwareTypeReceiver.receiver = topLevelReceiver
    }

    private
    fun isSoftwareTypeAccessor(accessor: ConfigureAccessor): Boolean {
        return accessor is ConfigureAccessorInternal.DefaultCustom && accessor.customAccessorIdentifier.startsWith("softwareType:")
    }
}
