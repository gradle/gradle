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

package org.gradle.configurationcache.build

import org.gradle.StartParameter
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.composite.internal.DefaultIncludedBuild
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.initialization.BuildCompletionListener
import org.gradle.initialization.BuildModelController
import org.gradle.initialization.BuildOptionBuildOperationProgressEventsEmitter
import org.gradle.initialization.DefaultGradleLauncher
import org.gradle.initialization.DefaultGradleLauncherFactory
import org.gradle.initialization.GradleLauncher
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.initialization.internal.InternalBuildFinishedListener
import org.gradle.initialization.layout.BuildLayout
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.build.BuildState
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.BuildScopeServices
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.util.Path


open class ConfigurationCacheIncludedBuildState(
    buildIdentifier: BuildIdentifier,
    identityPath: Path,
    buildDefinition: BuildDefinition,
    isImplicit: Boolean,
    owner: BuildState,
    parentLease: WorkerLeaseRegistry.WorkerLease
) : DefaultIncludedBuild(buildIdentifier, identityPath, buildDefinition, isImplicit, owner, parentLease) {

    override fun createGradleLauncher(): GradleLauncher {
        return nestedBuildFactoryInternal().nestedInstance(
            buildDefinition,
            this,
            { parent ->
                // Avoid BuildLayout discovery
                object : BuildScopeServices(parent) {
                    override fun createBuildLayout(buildLayoutFactory: BuildLayoutFactory, startParameter: StartParameter) =
                        buildDefinition.buildRootDir.let { rootDir ->
                            BuildLayout(rootDir, rootDir, null)
                        }
                }
            },
            { gradle: GradleInternal, _: BuildModelController, serviceRegistry: BuildScopeServices, servicesToStop: List<*> ->
                // Create a GradleLauncher with a no-op model controller
                val controller = NoOpBuildModelController(gradle)
                val listenerManager = serviceRegistry.get(ListenerManager::class.java)
                DefaultGradleLauncher(
                    gradle,
                    controller,
                    serviceRegistry.get(ExceptionAnalyser::class.java),
                    gradle.buildListenerBroadcaster,
                    listenerManager.getBroadcaster(BuildCompletionListener::class.java),
                    listenerManager.getBroadcaster(InternalBuildFinishedListener::class.java),
                    gradle.serviceOf(),
                    serviceRegistry,
                    servicesToStop,
                    BuildOptionBuildOperationProgressEventsEmitter(gradle.serviceOf())
                )
            }
        )
    }

    private
    fun nestedBuildFactoryInternal(): DefaultGradleLauncherFactory.NestedBuildFactoryInternal =
        owner.nestedBuildFactory as DefaultGradleLauncherFactory.NestedBuildFactoryInternal
}


// The model for this build is already fully populated and the tasks scheduled when it is created, so this controller does not need to do anything
private
class NoOpBuildModelController(val gradle: GradleInternal) : BuildModelController {
    // TODO - this method should fail, as the fully configured settings object is not actually available
    override fun getLoadedSettings() = gradle.settings
    // TODO - this method should fail, as the fully configured build model is not actually available
    override fun getConfiguredModel() = gradle
    // TODO - this method should fail, as the tasks are already scheduled for this build
    override fun scheduleTasks(tasks: Iterable<String>) {
    }
    override fun scheduleRequestedTasks() {
        // Already done
    }
}
