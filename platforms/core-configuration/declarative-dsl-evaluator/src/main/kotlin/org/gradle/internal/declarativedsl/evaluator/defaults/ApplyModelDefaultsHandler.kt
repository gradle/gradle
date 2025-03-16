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

package org.gradle.internal.declarativedsl.evaluator.defaults

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


class ApplyModelDefaults : InterpretationStepFeature.ResolutionResultPostprocessing.ApplyModelDefaults, Serializable


interface ApplyModelDefaultsHandler : ResolutionResultHandler {

    fun getDefaultsResolutionResults(resolutionResult: ResolutionResult): List<ModelDefaultsResolutionResults>

    override fun shouldHandleFeature(feature: InterpretationStepFeature.ResolutionResultPostprocessing) =
        // Use an is-check, as the implementation might be a proxy
        feature is InterpretationStepFeature.ResolutionResultPostprocessing.ApplyModelDefaults

    override fun processResolutionResult(resolutionResult: ResolutionResult): ResolutionResult {
        with(DefaultsTransformer(resolutionResult.topLevelReceiver)) {
            val defaultsResolutionResultsToApply = getDefaultsResolutionResults(resolutionResult)
            // For the referenced software types, add their model defaults as operations mapped onto the top-level receiver
            val assignmentsFromDefaults = applyAssignmentDefaults(defaultsResolutionResultsToApply)
            val additionsFromDefaults = applyAdditionDefaults(defaultsResolutionResultsToApply)
            val nestedObjectAccessFromDefaults = applyNestedObjectAccessDefaults(defaultsResolutionResultsToApply)

            // Return a resolution result with the operations from model defaults added
            return resolutionResult.copy(
                assignmentsFromDefaults = resolutionResult.assignmentsFromDefaults + assignmentsFromDefaults,
                additionsFromDefaults = resolutionResult.additionsFromDefaults + additionsFromDefaults,
                nestedObjectAccessFromDefaults = resolutionResult.nestedObjectAccessFromDefaults + nestedObjectAccessFromDefaults
            )
        }
    }

    companion object {
        /**
         * A handler that does not apply any model defaults.  We use this during the main script processing step so that the interpretation
         * step will positively handle the {@link ApplyModelDefaults} feature.  However, most model defaults are applied by
         * the {@link DeclarativeModelDefaultsHandler} during application of the software type plugin.
         */
        val DO_NOTHING = object : ApplyModelDefaultsHandler {
            override fun getDefaultsResolutionResults(resolutionResult: ResolutionResult): List<ModelDefaultsResolutionResults> = emptyList()
            override fun processResolutionResult(resolutionResult: ResolutionResult): ResolutionResult = resolutionResult
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


interface ModelDefaultsRepository {
    fun findDefaults(softwareTypeName: String): ModelDefaultsResolutionResults?
}

fun defaultsForAllUsedSoftwareTypes(modelDefaultsRepository: ModelDefaultsRepository, resolutionResult: ResolutionResult) =
    findUsedSoftwareTypeNames(resolutionResult).mapNotNull(modelDefaultsRepository::findDefaults)



/**
 * Transformation logic for the kinds of resolution results that appear in defaults.
 * For any of the supported result records, supports replacing the `defaults { ... }` receiver in it with
 * the given [targetBaseReceiver].
 */
private
class DefaultsTransformer(
    private val targetBaseReceiver: ObjectOrigin.ReceiverOrigin
) {
    fun transfer(origin: ObjectOrigin) = replaceReceivers(origin, ::isDefaultsCall, targetBaseReceiver)

    fun applyAssignmentDefaults(
        defaultsResolutionResults: List<ModelDefaultsResolutionResults>
    ): List<AssignmentRecord> =
        defaultsResolutionResults.flatMap { modelDefault ->
            modelDefault.assignments.map { assignmentRecord ->
                assignmentRecord.copy(
                    lhs = assignmentRecord.lhs.copy(receiverObject = transfer(assignmentRecord.lhs.receiverObject)),
                    rhs = transfer(assignmentRecord.rhs)
                )
            }
        }

    fun applyAdditionDefaults(
        defaultsResolutionResults: List<ModelDefaultsResolutionResults>,
    ): List<DataAdditionRecord> =
        defaultsResolutionResults.flatMap { modelDefault ->
            modelDefault.additions.map { additionRecord ->
                DataAdditionRecord(transfer(additionRecord.container), transfer(additionRecord.dataObject))
            }
        }

    fun applyNestedObjectAccessDefaults(
        defaultsResolutionResults: List<ModelDefaultsResolutionResults>
    ): List<NestedObjectAccessRecord> =
        defaultsResolutionResults.flatMap { modelDefault ->
            modelDefault.nestedObjectAccess.map { accessRecord ->
                NestedObjectAccessRecord(
                    container = transfer(accessRecord.container),
                    // Expect that the type remains the same: the only thing that will be mapped to a different type is the `defaults { ... }`
                    dataObject = transfer(accessRecord.dataObject) as ObjectOrigin.AccessAndConfigureReceiver
                )
            }
        }
}
