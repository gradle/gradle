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

import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult


/**
 * Processes a resolution result to extract the project type model defaults operations it defines.
 */
internal
object ModelDefaultsResolutionProcessor {
    fun process(resolutionResult: ResolutionResult): Map<String, ModelDefaultsResolutionResults> {
        val assignments = resolutionResult.assignments.groupBy { assignment ->
            getProjectFeature(assignment.lhs.receiverObject).function.simpleName
        }
        val additions = resolutionResult.additions.groupBy { addition ->
            getProjectFeature(addition.container).function.simpleName
        }
        val nestedObjectAccess = resolutionResult.nestedObjectAccess.mapNotNull { access ->
            findProjectFeature(access.dataObject)?.let { access to it.function.simpleName }
        }.groupBy({ (_, projectFeatureName) -> projectFeatureName }, valueTransform = { (access, _) -> access })

        val projectFeatureNames = assignments.keys + additions.keys + nestedObjectAccess.keys

        return projectFeatureNames.associateWith {
            ModelDefaultsResolutionResults(it, assignments[it].orEmpty(), additions[it].orEmpty(), nestedObjectAccess[it].orEmpty())
        }
    }
}


/**
 * The operations for model defaults extracted from a resolution result.
 */
data class ModelDefaultsResolutionResults(
    val projectFeatureName: String,
    val assignments: List<AssignmentRecord>,
    val additions: List<DataAdditionRecord>,
    val nestedObjectAccess: List<NestedObjectAccessRecord>
)


/**
 * Searches an ObjectOrigin receiver hierarchy to find the parent project type or throws an error if a project type
 * is not in the hierarchy.
 */
private
fun getProjectFeature(objectOrigin: ObjectOrigin): ObjectOrigin.AccessAndConfigureReceiver =
    findProjectFeature(objectOrigin) ?: error("could not discover project feature for $objectOrigin")


/**
 * Searches an ObjectOrigin receiver hierarchy to find the parent project type. Returns null if a project type
 * is not in the hierarchy.
 */
private
fun findProjectFeature(objectOrigin: ObjectOrigin): ObjectOrigin.AccessAndConfigureReceiver? =
    when (objectOrigin) {
        is ObjectOrigin.ImplicitThisReceiver -> findProjectFeature(objectOrigin.resolvedTo)
        is ObjectOrigin.AccessAndConfigureReceiver ->
            if (isProjectFeature(objectOrigin)) objectOrigin else findProjectFeature(objectOrigin.receiver)
        is ObjectOrigin.NewObjectFromMemberFunction -> findProjectFeature(objectOrigin.receiver)
        is ObjectOrigin.TopLevelReceiver -> null
        else -> null
    }


/**
 * Checks if a given ObjectOrigin is a project type configuration block.
 */
internal
fun isProjectFeature(objectOrigin: ObjectOrigin): Boolean =
    true == (objectOrigin as? ObjectOrigin.AccessAndConfigureReceiver)?.receiver?.let { receiver ->
        (receiver as? ObjectOrigin.ImplicitThisReceiver)?.resolvedTo?.let { parent ->
            isDefaultsCall(parent)
        }
    }


/**
 * Checks is a given ObjectOrigin receiver is a call to the `defaults` function.
 */
internal
fun isDefaultsCall(parent: ObjectOrigin.ReceiverOrigin) = parent is ObjectOrigin.AccessAndConfigureReceiver &&
    isTopLevelReceiver(parent.receiver) &&
    (parent as? ObjectOrigin.AccessAndConfigureReceiver)?.function?.simpleName == DEFAULTS_BLOCK_NAME


/**
 * Checks if a given ObjectOrigin receiver is the top-level receiver.
 */
private
fun isTopLevelReceiver(objectOrigin: ObjectOrigin) =
    (objectOrigin as? ObjectOrigin.ImplicitThisReceiver)?.resolvedTo is ObjectOrigin.TopLevelReceiver
