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

package org.gradle.internal.cc.impl

import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.api.internal.FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.api.invocation.Gradle
import org.gradle.execution.ExecutionAccessListener
import org.gradle.internal.DeprecatedInGradleScope
import org.gradle.internal.InternalListener
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Reports deprecations for behaviors unsupported with CC even if CC is not enabled.
 * Some deprecations are skipped if CC is in use since the deprecated features are already reported as problems in that case.
 */
@ListenerService
@ServiceScope(Scope.BuildTree::class)
internal
class DeprecatedFeaturesListener(
    private val featureFlags: FeatureFlags,
    private val buildModelParameters: BuildModelParameters
) : BuildScopeListenerRegistrationListener, TaskExecutionAccessListener, ExecutionAccessListener {

    override fun onBuildScopeListenerRegistration(listener: Any, invocationDescription: String, invocationSource: Any) {
        when {
            invocationDescription == "Gradle.useLogger" && listener !is InternalListener -> {
                // Some configuration-time only listeners are allowed by CC, but Gradle.useLogger is deprecated for everything.
                // A couple of places in Gradle continue calling useLogger until this method is removed, and we suppress the
                // warning for their InternalListeners here.
                DeprecationLogger.deprecateMethod(Gradle::class.java, "useLogger(Object)")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "deprecated_use_logger")
                    .nagUser();
            }
            shouldNagAbout(listener) -> {
                nagUserAbout("Listener registration using $invocationDescription()", 7, "task_execution_events")
            }
        }
    }

    override fun onProjectAccess(invocationDescription: String, task: TaskInternal, runningTask: TaskInternal?) {
        if (shouldNagFor(task, runningTask, ignoreStable = true)) {
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
    fun shouldNagFor(task: TaskInternal, runningTask: TaskInternal?, ignoreStable: Boolean = false) =
        shouldNag(ignoreStable) && shouldReportInContext(task, runningTask)

    private
    fun shouldNag(ignoreStable: Boolean = false): Boolean =
        // TODO:configuration-cache - this listener shouldn't be registered when cc is enabled
        !buildModelParameters.isConfigurationCache && (ignoreStable || featureFlags.isEnabled(STABLE_CONFIGURATION_CACHE))

    private
    fun shouldNagAbout(listener: Any): Boolean = shouldNag() && !isSupportedListener(listener)

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

/**
 * Checks if the build listener is allowed to be registered with the configuration cache enabled.
 */
internal
fun isSupportedListener(listener: Any): Boolean {
    return when {
        // Internal listeners are always allowed: we know their lifecycle and ensure there are no problems when configuration cache is reused.
        listener is InternalListener -> true
        // Explicitly unsupported Listener types are disallowed.
        JavaReflectionUtil.hasAnnotation(listener.javaClass, DeprecatedInGradleScope::class.java) -> false
        // We had to check for unsupported first to reject a listener that implements both allowed and disallowed interfaces.
        listener is ProjectEvaluationListener || listener is TaskExecutionGraphListener || listener is DependencyResolutionListener -> true
        // Just reject everything we don't know.
        else -> false
    }
}
