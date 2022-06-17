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

package org.gradle.configurationcache

import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultLocalComponentRegistry
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentInAnotherBuildProvider
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.DefaultCrossProjectModelAccess
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.configuration.ProjectsPreparer
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.configuration.project.BuildScriptProcessor
import org.gradle.configuration.project.ConfigureActionsProjectEvaluator
import org.gradle.configuration.project.DelayedConfigurationActions
import org.gradle.configuration.project.LifecycleProjectEvaluator
import org.gradle.configuration.project.PluginsProjectConfigureActions
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.configurationcache.extensions.get
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.initialization.ConfigurationCacheBuildEnablement
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.configurationcache.services.ConfigurationCacheEnvironment
import org.gradle.configurationcache.services.DefaultEnvironment
import org.gradle.execution.DefaultTaskSchedulingPreparer
import org.gradle.execution.ExcludedTaskFilteringProjectsPreparer
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.initialization.VintageBuildModelController
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildModelController
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.CachingServiceLocator
import org.gradle.internal.service.scopes.BuildScopeServices
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.invocation.DefaultGradle


class DefaultBuildModelControllerServices(
    private val buildModelParameters: BuildModelParameters,
) : BuildModelControllerServices {
    override fun servicesForBuild(buildDefinition: BuildDefinition, owner: BuildState, parentBuild: BuildState?): BuildModelControllerServices.Supplier {
        return BuildModelControllerServices.Supplier { registration, services ->
            registration.add(BuildDefinition::class.java, buildDefinition)
            registration.add(BuildState::class.java, owner)
            registration.addProvider(ServicesProvider(buildDefinition, parentBuild, services))
            registration.add(DeprecatedFeaturesListenerManagerAction::class.java)
            if (buildModelParameters.isConfigurationCache) {
                registration.add(ConfigurationCacheBuildEnablement::class.java)
                registration.add(ConfigurationCacheProblemsListenerManagerAction::class.java)
                registration.addProvider(ConfigurationCacheBuildControllerProvider())
                registration.add(ConfigurationCacheEnvironment::class.java)
            } else {
                registration.addProvider(VintageBuildControllerProvider())
                registration.add(DefaultEnvironment::class.java)
            }
            if (buildModelParameters.isIsolatedProjects) {
                registration.addProvider(ConfigurationCacheIsolatedProjectsProvider())
            } else {
                registration.addProvider(VintageIsolatedProjectsProvider())
            }
            if (buildModelParameters.isIntermediateModelCache) {
                registration.addProvider(ConfigurationCacheModelProvider())
            } else {
                registration.addProvider(VintageModelProvider())
            }
        }
    }

    private
    class ServicesProvider(
        private val buildDefinition: BuildDefinition,
        private val parentBuild: BuildState?,
        private val buildScopeServices: BuildScopeServices
    ) {
        fun createGradleModel(instantiator: Instantiator, serviceRegistryFactory: ServiceRegistryFactory): GradleInternal? {
            return instantiator.newInstance(
                DefaultGradle::class.java,
                parentBuild,
                buildDefinition.startParameter,
                serviceRegistryFactory
            )
        }

        fun createBuildLifecycleController(buildLifecycleControllerFactory: BuildLifecycleControllerFactory): BuildLifecycleController {
            return buildLifecycleControllerFactory.newInstance(buildDefinition, buildScopeServices)
        }
    }

    private
    class ConfigurationCacheBuildControllerProvider {
        fun createBuildModelController(
            gradle: GradleInternal,
            stateTransitionControllerFactory: StateTransitionControllerFactory,
            cache: BuildTreeConfigurationCache
        ): BuildModelController {
            val vintageController = VintageBuildControllerProvider().createBuildModelController(gradle, stateTransitionControllerFactory)
            return ConfigurationCacheAwareBuildModelController(gradle, vintageController, cache)
        }
    }

    private
    class VintageBuildControllerProvider {
        fun createBuildModelController(
            gradle: GradleInternal,
            stateTransitionControllerFactory: StateTransitionControllerFactory
        ): BuildModelController {
            val projectsPreparer: ProjectsPreparer = gradle.services.get()
            val taskSchedulingPreparer = DefaultTaskSchedulingPreparer(ExcludedTaskFilteringProjectsPreparer(gradle.services.get()))
            val settingsPreparer: SettingsPreparer = gradle.services.get()
            val taskExecutionPreparer: TaskExecutionPreparer = gradle.services.get()
            return VintageBuildModelController(gradle, projectsPreparer, taskSchedulingPreparer, settingsPreparer, taskExecutionPreparer, stateTransitionControllerFactory)
        }
    }

    private
    class ConfigurationCacheIsolatedProjectsProvider {
        fun createCrossProjectModelAccess(
            projectRegistry: ProjectRegistry<ProjectInternal>,
            problemsListener: ProblemsListener,
            userCodeApplicationContext: UserCodeApplicationContext,
            listenerManager: ListenerManager
        ): CrossProjectModelAccess {
            val delegate = VintageIsolatedProjectsProvider().createCrossProjectModelAccess(projectRegistry)
            return ProblemReportingCrossProjectModelAccess(delegate, problemsListener, listenerManager.getBroadcaster(CoupledProjectsListener::class.java), userCodeApplicationContext)
        }
    }

    private
    class VintageIsolatedProjectsProvider {
        fun createCrossProjectModelAccess(
            projectRegistry: ProjectRegistry<ProjectInternal>
        ): CrossProjectModelAccess {
            return DefaultCrossProjectModelAccess(projectRegistry)
        }
    }

    private
    class ConfigurationCacheModelProvider {
        fun createProjectEvaluator(
            buildOperationExecutor: BuildOperationExecutor,
            cachingServiceLocator: CachingServiceLocator,
            scriptPluginFactory: ScriptPluginFactory,
            fingerprintController: ConfigurationCacheFingerprintController,
            cancellationToken: BuildCancellationToken
        ): ProjectEvaluator {
            val evaluator = VintageModelProvider().createProjectEvaluator(buildOperationExecutor, cachingServiceLocator, scriptPluginFactory, cancellationToken)
            return ConfigurationCacheAwareProjectEvaluator(evaluator, fingerprintController)
        }

        fun createLocalComponentRegistry(
            currentBuild: BuildState,
            projectStateRegistry: ProjectStateRegistry,
            calculatedValueContainerFactory: CalculatedValueContainerFactory,
            cache: BuildTreeConfigurationCache,
            provider: LocalComponentProvider,
            otherBuildProvider: LocalComponentInAnotherBuildProvider
        ): LocalComponentRegistry {
            val effectiveProvider = ConfigurationCacheAwareLocalComponentProvider(provider, cache)
            return VintageModelProvider().createLocalComponentRegistry(currentBuild, projectStateRegistry, calculatedValueContainerFactory, effectiveProvider, otherBuildProvider)
        }
    }

    private
    class VintageModelProvider {
        fun createProjectEvaluator(
            buildOperationExecutor: BuildOperationExecutor,
            cachingServiceLocator: CachingServiceLocator,
            scriptPluginFactory: ScriptPluginFactory,
            cancellationToken: BuildCancellationToken
        ): ProjectEvaluator {
            val withActionsEvaluator = ConfigureActionsProjectEvaluator(
                PluginsProjectConfigureActions.from(cachingServiceLocator),
                BuildScriptProcessor(scriptPluginFactory),
                DelayedConfigurationActions()
            )
            return LifecycleProjectEvaluator(buildOperationExecutor, withActionsEvaluator, cancellationToken)
        }

        fun createLocalComponentRegistry(
            currentBuild: BuildState,
            projectStateRegistry: ProjectStateRegistry,
            calculatedValueContainerFactory: CalculatedValueContainerFactory,
            provider: LocalComponentProvider,
            otherBuildProvider: LocalComponentInAnotherBuildProvider
        ): LocalComponentRegistry {
            return DefaultLocalComponentRegistry(currentBuild.buildIdentifier, projectStateRegistry, calculatedValueContainerFactory, provider, otherBuildProvider)
        }
    }
}
