/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction


internal
class DeprecatedFeaturesListenerManagerAction(
    private val featurePreviews: FeaturePreviews
) : BuildScopeListenerManagerAction {
    override fun execute(manager: ListenerManager) {
        manager.addListener(DeprecatedFeaturesListener(featurePreviews))
    }

    private
    class DeprecatedFeaturesListener(
        private val featurePreviews: FeaturePreviews
    ) : BuildScopeListenerRegistrationListener, TaskExecutionAccessListener {
        override fun onBuildScopeListenerRegistration(listener: Any, invocationDescription: String, invocationSource: Any) {
            if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE)) {
                DeprecationLogger.deprecateAction("Listener registration using $invocationDescription()")
                    .willBecomeAnErrorInGradle8()
                    .withUpgradeGuideSection(7, "task_execution_events")
                    .nagUser()
            }
        }

        override fun onProjectAccess(invocationDescription: String, task: TaskInternal) {
            if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE)) {
                DeprecationLogger.deprecateAction("Invocation of $invocationDescription at execution time")
                    .willBecomeAnErrorInGradle8()
                    .withUpgradeGuideSection(7, "task_project")
                    .nagUser()
            }
        }

        override fun onTaskDependenciesAccess(invocationDescription: String, task: TaskInternal) {
            if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE)) {
                DeprecationLogger.deprecateAction("Invocation of $invocationDescription at execution time")
                    .willBecomeAnErrorInGradle8()
                    .withUpgradeGuideSection(7, "task_dependencies")
                    .nagUser()
            }
        }
    }
}
