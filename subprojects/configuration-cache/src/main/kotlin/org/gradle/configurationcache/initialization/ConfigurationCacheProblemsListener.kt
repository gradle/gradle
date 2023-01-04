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
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal.BUILD_SRC
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.credentials.CredentialListener
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.configurationcache.InputTrackingState
import org.gradle.configurationcache.problems.DocumentationSection.RequirementsBuildListeners
import org.gradle.configurationcache.problems.DocumentationSection.RequirementsExternalProcess
import org.gradle.configurationcache.problems.DocumentationSection.RequirementsSafeCredentials
import org.gradle.configurationcache.problems.DocumentationSection.RequirementsUseProjectDuringExecution
import org.gradle.configurationcache.problems.ProblemFactory
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.StructuredMessage
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.execution.TaskExecutionTracker
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scopes.BuildTree::class)
interface ConfigurationCacheProblemsListener : TaskExecutionAccessListener, BuildScopeListenerRegistrationListener, ExternalProcessStartedListener, CredentialListener


@ListenerService
class DefaultConfigurationCacheProblemsListener internal constructor(
    private val problems: ProblemsListener,
    private val problemFactory: ProblemFactory,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val taskExecutionTracker: TaskExecutionTracker,
    private val featureFlags: FeatureFlags,
    private val inputTrackingState: InputTrackingState,
) : ConfigurationCacheProblemsListener {

    override fun onProjectAccess(invocationDescription: String, task: TaskInternal) {
        if (atConfigurationTime()) {
            return
        }
        onTaskExecutionAccessProblem(invocationDescription, task)
    }

    override fun onTaskDependenciesAccess(invocationDescription: String, task: TaskInternal) {
        if (atConfigurationTime()) {
            return
        }
        onTaskExecutionAccessProblem(invocationDescription, task)
    }

    override fun onExternalProcessStarted(command: String, consumer: String?) {
        if (!isStableConfigurationCacheEnabled() || !atConfigurationTime() || isExecutingTask() || isInputTrackingDisabled()) {
            return
        }
        problems.onProblem(
            PropertyProblem(
                problemFactory.locationForCaller(consumer),
                StructuredMessage.build {
                    text("external process started ")
                    reference(command)
                },
                InvalidUserCodeException("Starting an external process '$command' during configuration time is unsupported."),
                documentationSection = RequirementsExternalProcess
            )
        )
    }

    private
    fun onTaskExecutionAccessProblem(invocationDescription: String, task: TaskInternal) {
        problemsListenerFor(task).onProblem(
            problemFactory.problem {
                text("invocation of ")
                reference(invocationDescription)
                text(" at execution time is unsupported.")
            }
                .exception("Invocation of '$invocationDescription' by $task at execution time is unsupported.")
                .documentationSection(RequirementsUseProjectDuringExecution)
                .mapLocation { locationForTask(it, task) }
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

    override fun onUnsafeCredentials(locationSpecificReason: String, task: TaskInternal) {
        problems.onProblem(
            problemFactory.problem {
                text("Credential values found in configuration for: ")
                text(locationSpecificReason)
            }
                .exception()
                .documentationSection(RequirementsSafeCredentials)
                .mapLocation { locationForTask(it, task) }
                .build()
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
    fun atConfigurationTime() =
        configurationTimeBarrier.isAtConfigurationTime

    private
    fun isInputTrackingDisabled() = !inputTrackingState.isEnabledForCurrentThread()

    private
    fun isStableConfigurationCacheEnabled() = featureFlags.isEnabled(FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE)

    private
    fun isExecutingTask() = taskExecutionTracker.currentTask.isPresent
}
