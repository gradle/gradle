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
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.instantexecution.problems.InstantExecutionProblems
import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.PropertyTrace
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.instantexecution.problems.location
import org.gradle.internal.InternalListener
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scopes.Build)
interface InstantExecutionProblemsListener : TaskExecutionAccessListener, BuildScopeListenerRegistrationListener


class DefaultInstantExecutionProblemsListener internal constructor(
    private val problems: InstantExecutionProblems,
    private val userCodeApplicationContext: UserCodeApplicationContext
) : InstantExecutionProblemsListener {

    override fun onProjectAccess(invocationDescription: String, task: TaskInternal) {
        onTaskExecutionAccessProblem(invocationDescription, task)
    }

    override fun onTaskDependenciesAccess(invocationDescription: String, task: TaskInternal) {
        onTaskExecutionAccessProblem(invocationDescription, task)
    }

    private
    fun onTaskExecutionAccessProblem(invocationDescription: String, task: TaskInternal) {
        val exception = InvalidUserCodeException(
            "Invocation of '$invocationDescription' by $task at execution time is unsupported."
        )
        val currentApplication = userCodeApplicationContext.current()
        val location = if (currentApplication != null) {
            PropertyTrace.BuildLogic(currentApplication.displayName)
        } else {
            PropertyTrace.Task(GeneratedSubclasses.unpackType(task), task.identityPath.path)
        }
        problems.onProblem(taskExecutionAccessProblem(
            location,
            invocationDescription,
            exception
        ))
    }

    private
    fun taskExecutionAccessProblem(trace: PropertyTrace, invocationDescription: String, exception: InvalidUserCodeException) =
        PropertyProblem(
            trace,
            StructuredMessage.build {
                text("invocation of ")
                reference(invocationDescription)
                text(" at execution time is unsupported.")
            },
            exception,
            documentationSection = "config_cache:requirements:use_project_during_execution"
        )

    override fun onBuildScopeListenerRegistration(listener: Any, invocationDescription: String, invocationSource: Any) {
        if (listener !is InternalListener && listener !is ProjectEvaluationListener) {
            val exception = InvalidUserCodeException(
                "Listener registration '$invocationDescription' by $invocationSource is unsupported."
            )
            problems.onProblem(listenerRegistrationProblem(
                userCodeApplicationContext.location(null),
                invocationDescription,
                exception
            ))
        }
    }

    private
    fun listenerRegistrationProblem(
        trace: PropertyTrace,
        invocationDescription: String,
        exception: InvalidUserCodeException
    ) =
        PropertyProblem(
            trace,
            StructuredMessage.build {
                text("registration of listener on ")
                reference(invocationDescription)
                text(" is unsupported")
            },
            exception,
            documentationSection = "config_cache:requirements:build_listeners"
        )
}


class NoOpInstantExecutionProblemsListener : InstantExecutionProblemsListener {
    override fun onProjectAccess(invocationDescription: String, task: TaskInternal) {
    }

    override fun onTaskDependenciesAccess(invocationDescription: String, task: TaskInternal) {
    }

    override fun onBuildScopeListenerRegistration(listener: Any, invocationDescription: String, invocationSource: Any) {
    }
}
