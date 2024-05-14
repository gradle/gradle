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
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult

class ConventionsResolutionProcessor {

    fun process(resolutionResult: ResolutionResult) : Map<String, AssignmentRecord> {
        return resolutionResult.assignments.associateBy { assignment ->
            getSoftwareType(assignment.lhs.receiverObject).function.simpleName
        }
    }
}

fun getSoftwareType(objectOrigin: ObjectOrigin) : ObjectOrigin.AccessAndConfigureReceiver {
    when(objectOrigin) {
        is ObjectOrigin.ImplicitThisReceiver -> {
            return getSoftwareType(objectOrigin.resolvedTo)
        }
        is ObjectOrigin.AccessAndConfigureReceiver -> {
            return if (isSoftwareType(objectOrigin)) {
                objectOrigin
            } else {
                getSoftwareType(objectOrigin.receiver)
            }
        }
        is ObjectOrigin.TopLevelReceiver -> {
            error("Could not discover softwareType for $objectOrigin")
        }
        else -> {
            error("Could not discover softwareType for $objectOrigin")
        }
    }
}

fun isSoftwareType(objectOrigin: ObjectOrigin.AccessAndConfigureReceiver) : Boolean {
    if (objectOrigin.receiver is ObjectOrigin.ImplicitThisReceiver &&
        (objectOrigin.receiver as ObjectOrigin.ImplicitThisReceiver).resolvedTo is ObjectOrigin.AccessAndConfigureReceiver) {
        val parent = (objectOrigin.receiver as ObjectOrigin.ImplicitThisReceiver).resolvedTo as ObjectOrigin.AccessAndConfigureReceiver
        if (parent.function.simpleName == "conventions") {
            return true
        }
    }
    return false
}
