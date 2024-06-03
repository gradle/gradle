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

package org.gradle.configurationcache.initialization

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.api.internal.ExternalProcessStartedListener
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal.BUILD_SRC
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.configurationcache.InputTrackingState
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsBuildListeners
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsExternalProcess
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsUseProjectDuringExecution
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.configurationcache.serialization.Workarounds.canAccessConventions
import org.gradle.execution.ExecutionAccessListener
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.BuildTree::class)
interface ConfigurationCacheProblemsListener : ExecutionAccessListener, TaskExecutionAccessListener, BuildScopeListenerRegistrationListener, ExternalProcessStartedListener


@ListenerService
class DefaultConfigurationCacheProblemsListener internal constructor(
    private val problems: ProblemsListener,
    private val problemFactory: ProblemFactory,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val workExecutionTracker: WorkExecutionTracker,
    private val inputTrackingState: InputTrackingState
) : ConfigurationCacheProblemsListener {

    override fun disallowedAtExecutionInjectedServiceAccessed(injectedServiceType: Class<*>, getterName: String, consumer: String) {
        val problem = problemFactory.problem(consumer) {
            text("accessing non-serializable type ")
            reference(injectedServiceType)
            text(" caused by invocation ")
            reference(getterName)
        }
            .exception("Accessing non-serializable type '$injectedServiceType' during execution time is unsupported.")
            .documentationSection(DocumentationSection.RequirementsDisallowedTypes)
            .build()
        problems.onProblem(problem)
    }

    override fun onProjectAccess(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        onTaskExecutionAccessProblem(invocationDescription, task, runningTask)
    }

    override fun onConventionAccess(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        if (canAccessConventions(task.javaClass.name, invocationDescription)) {
            return
        }
        onTaskExecutionAccessProblem(invocationDescription, task, runningTask)
    }

    override fun onTaskDependenciesAccess(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        onTaskExecutionAccessProblem(invocationDescription, task, runningTask)
    }

    override fun onExternalProcessStarted(command: String, consumer: String?) {
        if (!atConfigurationTime() || isExecutingWork() || isInputTrackingDisabled()) {
            return
        }
        val problem = problemFactory.problem(consumer) {
            text("external process started ")
            reference(command)
        }
            .exception("Starting an external process '$command' during configuration time is unsupported.")
            .documentationSection(RequirementsExternalProcess)
            .build()
        problems.onProblem(problem)
    }

    private
    fun onTaskExecutionAccessProblem(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        // It is possible that another task is being executed now and causes `task` to be configured.
        // In this case, we shouldn't attribute the error to the `task` alone, as it can be misleading,
        // and that usage can be benign. This is especially important when the currently running task is
        // marked as `notCompatibleWithConfigurationCache` - attributing the error to `task` will cause
        // the build to fail when it really shouldn't.
        val contextTask = runningTask ?: task
        val isExecutingOtherTask = contextTask != task
        problemsListenerFor(contextTask).onProblem(
            problemFactory.problem {
                if (isExecutingOtherTask) {
                    text("execution of task ")
                    reference(contextTask.path)
                    text(" caused invocation of ")
                    reference(invocationDescription)
                    text(" in other task at execution time which is unsupported.")
                } else {
                    text("invocation of ")
                    reference(invocationDescription)
                    text(" at execution time is unsupported.")
                }
            }
                .exception(
                    if (isExecutingOtherTask) {
                        "Execution of $runningTask caused invocation of '$invocationDescription' by $task at execution time which is unsupported."
                    } else {
                        "Invocation of '$invocationDescription' by $task at execution time is unsupported."
                    }
                )
                .documentationSection(RequirementsUseProjectDuringExecution)
                .mapLocation { locationForTask(it, contextTask) }
                .build()
        )
    }

    private
    fun locationForTask(location: PropertyTrace, task: TaskInternal) =
        if (location is PropertyTrace.BuildLogic) {
            location
        } else {
            PropertyTrace.Task(GeneratedSubclasses.unpackType(task), task.identityPath.path)
        }

    private
    fun problemsListenerFor(task: TaskInternal): ProblemsListener = when {
        task.isCompatibleWithConfigurationCache -> problems
        else -> problems.forIncompatibleTask(task.identityPath.path)
    }

    override fun onBuildScopeListenerRegistration(listener: Any, invocationDescription: String, invocationSource: Any) {
        if (isBuildSrcBuild(invocationSource)) {
            return
        }
        problems.onProblem(
            listenerRegistrationProblem(
                invocationDescription,
                InvalidUserCodeException(
                    "Listener registration '$invocationDescription' by $invocationSource is unsupported."
                )
            )
        )
    }

    private
    fun listenerRegistrationProblem(
        invocationDescription: String,
        exception: InvalidUserCodeException
    ) = problemFactory.problem(
        StructuredMessage.build {
            text("registration of listener on ")
            reference(invocationDescription)
            text(" is unsupported")
        },
        exception,
        documentationSection = RequirementsBuildListeners
    )

    private
    fun isBuildSrcBuild(invocationSource: Any): Boolean =
        (invocationSource as? GradleInternal)?.run {
            !isRootBuild && identityPath.name == BUILD_SRC
        } ?: false

    private
    fun atConfigurationTime() = configurationTimeBarrier.isAtConfigurationTime

    private
    fun isInputTrackingDisabled() = !inputTrackingState.isEnabledForCurrentThread()

    private
    fun isExecutingWork() = workExecutionTracker.currentTask.isPresent || workExecutionTracker.isExecutingTransformAction
}
