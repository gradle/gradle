/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.execution.ExecutionAccessListener
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Reports deprecations when [FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE] is enabled
 * but the configuration cache is not (since the deprecated features are already reported as problems
 * in that case).
 */
@ListenerService
@ServiceScope(Scopes.BuildTree::class)
internal
class DeprecatedFeaturesListener(
    private val featureFlags: FeatureFlags,
    private val buildModelParameters: BuildModelParameters
) : BuildScopeListenerRegistrationListener, TaskExecutionAccessListener, ExecutionAccessListener {

    override fun onBuildScopeListenerRegistration(listener: Any, invocationDescription: String, invocationSource: Any) {
        if (shouldNagForListener(listener, invocationSource)) {
            nagUserAbout("Listener registration using $invocationDescription()", 7, "task_execution_events")
        }
    }

    override fun onProjectAccess(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        if (shouldNagFor(task, runningTask)) {
            nagUserAbout("Invocation of $invocationDescription at execution time", 7, "task_project")
        }
    }

    override fun onTaskDependenciesAccess(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        if (shouldNagFor(task, runningTask)) {
            throwUnsupported("Invocation of $invocationDescription at execution time")
        }
    }

    override fun onConventionAccess(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        if (shouldNagFor(task, runningTask)) {
            nagUserAbout("Invocation of $invocationDescription at execution time", 8, "task_convention")
        }
    }

    override fun disallowedAtExecutionInjectedServiceAccessed(injectedServiceType: Class<*>, getterName: String, consumer: String) {
        if (shouldNag()) {
            throwUnsupported("Invocation of $injectedServiceType at execution time")
        }
    }

    private
    fun nagUserAbout(action: String, upgradeGuideMajorVersion: Int, upgradeGuideSection: String) {
        DeprecationLogger.deprecateAction(action)
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(upgradeGuideMajorVersion, upgradeGuideSection)
            .nagUser()
    }

    private
    fun shouldNagFor(task: TaskInternal, runningTask: TaskInternal?) =
        shouldNag() && shouldReportInContext(task, runningTask)

    // Only unconditionally nag about listeners/sources that are already deprecated.
    @Suppress("DEPRECATION")
    private
    fun shouldNagForListener(listener: Any, invocationSource: Any) =
        shouldNag() || listener is org.gradle.api.execution.TaskExecutionListener || listener is org.gradle.api.execution.TaskActionListener || invocationSource is TaskExecutionGraph

    private
    fun shouldNag(): Boolean =
        // TODO:configuration-cache - this listener shouldn't be registered when cc is enabled
        !buildModelParameters.isConfigurationCache && featureFlags.isEnabled(STABLE_CONFIGURATION_CACHE)

    /**
     * Only nag about tasks that are actually executing, but not tasks that are configured by the executing tasks.
     * A task is unlikely to reach out to other tasks without violating other constraints.
     **/
    private
    fun shouldReportInContext(task: TaskInternal, runningTask: TaskInternal?) =
        runningTask == null || task === runningTask

    private
    fun throwUnsupported(reason: String): Nothing =
        throw UnsupportedOperationException("$reason is unsupported with the STABLE_CONFIGURATION_CACHE feature preview.")
}
