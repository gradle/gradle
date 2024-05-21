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

package org.gradle.internal.declarativedsl.conventions

import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult


class ConventionsResolutionProcessor {
    fun process(resolutionResult: ResolutionResult): ProcessedConventions {
        val assignments = resolutionResult.assignments.groupBy { assignment ->
            getSoftwareType(assignment.lhs.receiverObject).function.simpleName
        }
        val additions = resolutionResult.additions.groupBy { addition ->
            getSoftwareType(addition.container).function.simpleName
        }
        val nestedObjectAccess = resolutionResult.nestedObjectAccess.mapNotNull { access ->
            findSoftwareType(access.dataObject)?.let { access to it.function.simpleName }
        }.groupBy({ (_, softwareTypeName) -> softwareTypeName }, valueTransform = { (access, _) -> access })

        return ProcessedConventions(assignments, additions, nestedObjectAccess)
    }
}


data class ProcessedConventions(
    val assignments: Map<String, List<AssignmentRecord>>,
    val additions: Map<String, List<DataAdditionRecord>>,
    val nestedObjectAccess: Map<String, List<NestedObjectAccessRecord>>
)


private
fun getSoftwareType(objectOrigin: ObjectOrigin): ObjectOrigin.AccessAndConfigureReceiver =
    findSoftwareType(objectOrigin) ?: error("Could not discover softwareType for $objectOrigin")


private
fun findSoftwareType(objectOrigin: ObjectOrigin): ObjectOrigin.AccessAndConfigureReceiver? =
    when (objectOrigin) {
        is ObjectOrigin.ImplicitThisReceiver -> findSoftwareType(objectOrigin.resolvedTo)
        is ObjectOrigin.AccessAndConfigureReceiver ->
            if (isSoftwareType(objectOrigin)) objectOrigin else findSoftwareType(objectOrigin.receiver)

        is ObjectOrigin.TopLevelReceiver -> null
        else -> null
    }


internal
fun isSoftwareType(objectOrigin: ObjectOrigin): Boolean =
    true == (objectOrigin as? ObjectOrigin.AccessAndConfigureReceiver)?.receiver?.let { receiver ->
        (receiver as? ObjectOrigin.ImplicitThisReceiver)?.resolvedTo?.let { parent ->
            isConventionsCall(parent)
        }
    }


internal
fun isConventionsCall(parent: ObjectOrigin.ReceiverOrigin) = parent is ObjectOrigin.AccessAndConfigureReceiver &&
    isTopLevelReceiver(parent.receiver) &&
    (parent as? ObjectOrigin.AccessAndConfigureReceiver)?.function?.simpleName == "conventions"


private
fun isTopLevelReceiver(objectOrigin: ObjectOrigin) =
    (objectOrigin as? ObjectOrigin.ImplicitThisReceiver)?.resolvedTo is ObjectOrigin.TopLevelReceiver
