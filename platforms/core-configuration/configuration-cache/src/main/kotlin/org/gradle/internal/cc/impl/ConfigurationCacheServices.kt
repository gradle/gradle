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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.TaskExecutionAccessChecker
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildModelParametersFactory
import org.gradle.internal.buildtree.control.DefaultBuildModelParametersFactory
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.cc.impl.serialize.ConfigurationCacheCodecs
import org.gradle.internal.cc.impl.serialize.DefaultConfigurationCacheCodecs
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices


class ConfigurationCacheServices : AbstractGradleModuleServices() {

    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BuildModelParametersFactory::class.java, DefaultBuildModelParametersFactory::class.java)
        }
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.run {
            // These services ensure cleanup of stale CC entries, which must be scheduled regardless of whether CC is used in the current invocation
            add(ConfigurationCacheRepository::class.java)
            add(ConfigurationCacheEntryCollector::class.java)
        }
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.run {
            addProvider(BuildTreeModelControllerServices)
        }
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.run {
            addProvider(BuildModelControllerServices)
            add(RelevantProjectsRegistry::class.java)
            addProvider(TaskExecutionAccessCheckerProvider)
            add(ConfigurationCacheHost::class.java, DefaultConfigurationCacheHost::class.java)
            add(ConfigurationCacheCodecs::class.java, DefaultConfigurationCacheCodecs::class.java)
            add(
                ConfigurationCacheBuildTreeIO::class.java,
                ConfigurationCacheIncludedBuildIO::class.java,
                DefaultConfigurationCacheIO::class.java
            )
        }
    }

    private
    object TaskExecutionAccessCheckerProvider : ServiceRegistrationProvider {
        @Provides
        fun createTaskExecutionAccessChecker(
            /** In non-CC builds, [BuildTreeConfigurationCache] is not registered; accepting a list here is a way to ignore its absence. */
            configurationCache: List<BuildTreeConfigurationCache>,
            configurationTimeBarrier: ConfigurationTimeBarrier,
            modelParameters: BuildModelParameters,
            /** In non-CC builds, [ConfigurationCacheStartParameter] is not registered; accepting a list here is a way to ignore its absence. */
            configurationCacheStartParameter: List<ConfigurationCacheStartParameter>,
            listenerManager: ListenerManager,
            workExecutionTracker: WorkExecutionTracker,
        ): TaskExecutionAccessChecker {
            val broadcast = listenerManager.getBroadcaster(TaskExecutionAccessListener::class.java)
            val workGraphLoadingState = workGraphLoadingStateFrom(configurationCache)
            return when {
                !modelParameters.isConfigurationCache -> TaskExecutionAccessCheckers.TaskStateBased(workGraphLoadingState, broadcast, workExecutionTracker)
                configurationCacheStartParameter.single().taskExecutionAccessPreStable -> TaskExecutionAccessCheckers.TaskStateBased(
                    workGraphLoadingState,
                    broadcast,
                    workExecutionTracker
                )

                else -> TaskExecutionAccessCheckers.ConfigurationTimeBarrierBased(configurationTimeBarrier, workGraphLoadingState, broadcast, workExecutionTracker)
            }
        }

        private
        fun workGraphLoadingStateFrom(maybeConfigurationCache: List<BuildTreeConfigurationCache>): WorkGraphLoadingState {
            if (maybeConfigurationCache.isEmpty()) {
                return WorkGraphLoadingState { false }
            }

            val configurationCache = maybeConfigurationCache.single()
            return WorkGraphLoadingState { configurationCache.isLoaded }
        }
    }
}
