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

package org.gradle.internal.declarativedsl.evaluator.conventions

import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.transformation.OriginReplacement.replaceReceivers
import org.gradle.internal.declarativedsl.evaluator.features.ResolutionResultHandler
import org.gradle.internal.declarativedsl.evaluator.softwareTypes.SOFTWARE_TYPE_ACCESSOR_PREFIX
import java.io.Serializable


class ConventionApplication : InterpretationStepFeature.ResolutionResultPostprocessing.ConventionApplication, Serializable


class ConventionApplicationHandler(private val softwareTypeConventionRepository: SoftwareTypeConventionRepository) : ResolutionResultHandler {
    override fun shouldHandleFeature(feature: InterpretationStepFeature.ResolutionResultPostprocessing) =
        // Use an is-check, as the implementation might be a proxy
        feature is InterpretationStepFeature.ResolutionResultPostprocessing.ConventionApplication

    override fun processResolutionResult(resolutionResult: ResolutionResult): ResolutionResult {
        // Find all the software types that are referenced in the build file
        val referencedSoftwareTypes = findUsedSoftwareTypeNames(resolutionResult)
            .mapNotNull(softwareTypeConventionRepository::findConventions)

        with(ConventionTransformer(resolutionResult.topLevelReceiver)) {
            // For the referenced software types, add their conventions as operations mapped onto the top-level receiver
            val conventionAssignments = applyAssignmentConventions(referencedSoftwareTypes)
            val conventionAdditions = applyAdditionConventions(referencedSoftwareTypes)
            val conventionNestedObjectAccess = applyNestedObjectAccessConvention(referencedSoftwareTypes)

            // Return a resolution result with the convention operations added
            return resolutionResult.copy(
                conventionAssignments = resolutionResult.conventionAssignments + conventionAssignments,
                conventionAdditions = resolutionResult.conventionAdditions + conventionAdditions,
                conventionNestedObjectAccess = resolutionResult.conventionNestedObjectAccess + conventionNestedObjectAccess
            )
        }
    }
}


internal
fun findUsedSoftwareTypeNames(resolutionResult: ResolutionResult): Set<String> {
    fun ConfigureAccessor.softwareTypeNameOrNull(): String? =
        if (this is ConfigureAccessor.Custom)
            customAccessorIdentifier.removePrefix("$SOFTWARE_TYPE_ACCESSOR_PREFIX:").takeIf { it != customAccessorIdentifier }
        else null

    return resolutionResult.nestedObjectAccess
        .mapNotNullTo(mutableSetOf()) { it.dataObject.accessor.softwareTypeNameOrNull() }
}


interface SoftwareTypeConventionRepository {
    fun findConventions(softwareTypeName: String): SoftwareTypeConventionResolutionResults?
}


/**
 * Transformation logic for the kinds of resolution results that appear in conventions.
 * For any of the supported result records, supports replacing the `conventions { ... }` receiver in it with
 * the given [targetBaseReceiver].
 */
private
class ConventionTransformer(
    private val targetBaseReceiver: ObjectOrigin.ReceiverOrigin
) {
    fun transfer(origin: ObjectOrigin) = replaceReceivers(origin, ::isConventionsCall, targetBaseReceiver)

    fun applyAssignmentConventions(
        referencedSoftwareTypes: List<SoftwareTypeConventionResolutionResults>
    ): List<AssignmentRecord> =
        referencedSoftwareTypes.flatMap { softwareType ->
            softwareType.assignments.map { assignmentRecord ->
                assignmentRecord.copy(
                    lhs = assignmentRecord.lhs.copy(receiverObject = transfer(assignmentRecord.lhs.receiverObject)),
                    rhs = transfer(assignmentRecord.rhs)
                )
            }
        }

    fun applyAdditionConventions(
        referencedSoftwareTypes: List<SoftwareTypeConventionResolutionResults>,
    ): List<DataAdditionRecord> =
        referencedSoftwareTypes.flatMap { softwareType ->
            softwareType.additions.map { additionRecord ->
                DataAdditionRecord(transfer(additionRecord.container), transfer(additionRecord.dataObject))
            }
        }

    fun applyNestedObjectAccessConvention(
        referencedSoftwareTypes: List<SoftwareTypeConventionResolutionResults>
    ): List<NestedObjectAccessRecord> =
        referencedSoftwareTypes.flatMap { softwareType ->
            softwareType.nestedObjectAccess.map { accessRecord ->
                NestedObjectAccessRecord(
                    container = transfer(accessRecord.container),
                    // Expect that the type remains the same: the only thing that will be mapped to a different type is the `conventions { ... }`
                    dataObject = transfer(accessRecord.dataObject) as ObjectOrigin.AccessAndConfigureReceiver
                )
            }
        }
}
