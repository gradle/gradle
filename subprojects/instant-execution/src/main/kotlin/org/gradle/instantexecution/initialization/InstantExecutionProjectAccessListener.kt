/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.initialization

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.ProjectAccessListener
import org.gradle.instantexecution.InstantExecutionReport
import org.gradle.instantexecution.serialization.PropertyProblem
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.StructuredMessage


class InstantExecutionProjectAccessListener internal constructor(

    private
    val startParameter: InstantExecutionStartParameter,

    private
    val report: InstantExecutionReport

) : ProjectAccessListener {

    override fun beforeRequestingTaskByPath(targetProject: ProjectInternal) = Unit

    override fun beforeResolvingProjectDependency(dependencyProject: ProjectInternal) = Unit

    override fun onProjectAccess(invocationDescription: String, invocationSource: Any) {
        if (startParameter.isEnabled) {
            val message = "invocation of '$invocationDescription' at execution time is unsupported."
            val exception = InvalidUserCodeException(message.capitalize())
            val trace =
                if (invocationSource is Task) PropertyTrace.Task(
                    GeneratedSubclasses.unpackType(invocationSource),
                    invocationSource.path
                )
                else PropertyTrace.Unknown
            report.add(PropertyProblem.Warning(
                trace,
                StructuredMessage.build { text(message) },
                exception
            ))
        }
    }
}
