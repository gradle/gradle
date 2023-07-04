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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.TaskExecutionAccessChecker
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.problems.ConfigurationCacheReport
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.configurationcache.services.RemoteScriptUpToDateChecker
import org.gradle.execution.ExecutionAccessChecker
import org.gradle.execution.ExecutionAccessListener
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry


class ConfigurationCacheServices : AbstractPluginServiceRegistry() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BeanConstructors::class.java)
        }
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.run {
            add(DefaultBuildTreeModelControllerServices::class.java)
        }
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.run {
            add(DefaultEncryptionService::class.java)
            add(ConfigurationCacheKey::class.java)
            add(ConfigurationCacheReport::class.java)
            add(DeprecatedFeaturesListener::class.java)
            add(DefaultBuildModelControllerServices::class.java)
            add(DefaultBuildToolingModelControllerFactory::class.java)
            add(ConfigurationCacheRepository::class.java)
            add(InputTrackingState::class.java)
            add(InstrumentedInputAccessListener::class.java)
            add(InstrumentedExecutionAccessListener::class.java)
            addProvider(RemoteScriptUpToDateCheckerProvider)
            addProvider(ExecutionAccessCheckerProvider)
        }
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.run {
            add(RelevantProjectsRegistry::class.java)
            addProvider(TaskExecutionAccessCheckerProvider)
        }
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheHost::class.java)
            add(ConfigurationCacheIO::class.java)
        }
    }

    private
    object RemoteScriptUpToDateCheckerProvider {
        fun createRemoteScriptUpToDateChecker(
            artifactCachesProvider: ArtifactCachesProvider,
            startParameter: ConfigurationCacheStartParameter,
            temporaryFileProvider: TemporaryFileProvider,
            fileStoreAndIndexProvider: FileStoreAndIndexProvider,
            resourceConnectorFactories: List<ResourceConnectorFactory>
        ): RemoteScriptUpToDateChecker =
            artifactCachesProvider.withWritableCache { _, cacheLockingManager ->
                RemoteScriptUpToDateChecker(
                    cacheLockingManager,
                    startParameter,
                    temporaryFileProvider,
                    fileStoreAndIndexProvider.externalResourceFileStore,
                    httpResourceConnectorFrom(resourceConnectorFactories),
                    fileStoreAndIndexProvider.externalResourceIndex
                )
            }

        private
        fun httpResourceConnectorFrom(resourceConnectorFactories: List<ResourceConnectorFactory>): ExternalResourceConnector =
            resourceConnectorFactories
                .single { "https" in it.supportedProtocols }
                .createResourceConnector(object : ResourceConnectorSpecification {})
    }

    private
    object ExecutionAccessCheckerProvider {

        fun createExecutionAccessChecker(
            listenerManager: ListenerManager,
            modelParameters: BuildModelParameters,
            configurationTimeBarrier: ConfigurationTimeBarrier
        ): ExecutionAccessChecker = when {
            modelParameters.isConfigurationCache -> {
                val broadcaster = listenerManager.getBroadcaster(ExecutionAccessListener::class.java)
                ConfigurationTimeBarrierBasedExecutionAccessChecker(configurationTimeBarrier, broadcaster)
            }

            else -> DefaultExecutionAccessChecker()
        }
    }

    private
    object TaskExecutionAccessCheckerProvider {
        fun createTaskExecutionAccessChecker(
            configurationTimeBarrier: ConfigurationTimeBarrier,
            modelParameters: BuildModelParameters,
            /** In non-CC builds, [ConfigurationCacheStartParameter] is not registered; accepting a list here is a way to ignore its absence. */
            configurationCacheStartParameter: List<ConfigurationCacheStartParameter>,
            listenerManager: ListenerManager,
            workExecutionTracker: WorkExecutionTracker,
        ): TaskExecutionAccessChecker {
            val broadcast = listenerManager.getBroadcaster(TaskExecutionAccessListener::class.java)
            return when {
                !modelParameters.isConfigurationCache -> TaskExecutionAccessCheckers.TaskStateBased(broadcast, workExecutionTracker)
                configurationCacheStartParameter.single().taskExecutionAccessPreStable -> TaskExecutionAccessCheckers.TaskStateBased(broadcast, workExecutionTracker)
                else -> TaskExecutionAccessCheckers.ConfigurationTimeBarrierBased(configurationTimeBarrier, broadcast, workExecutionTracker)
            }
        }
    }
}
