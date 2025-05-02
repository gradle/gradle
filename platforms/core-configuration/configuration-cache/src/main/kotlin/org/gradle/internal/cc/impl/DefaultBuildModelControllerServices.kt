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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.DefaultCrossProjectModelAccess
import org.gradle.api.internal.project.DefaultDynamicLookupRoutine
import org.gradle.api.internal.project.DynamicLookupRoutine
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.configuration.ProjectsPreparer
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.internal.DynamicCallContextTracker
import org.gradle.configuration.project.BuildScriptProcessor
import org.gradle.configuration.project.ConfigureActionsProjectEvaluator
import org.gradle.configuration.project.DelayedConfigurationActions
import org.gradle.configuration.project.LifecycleProjectEvaluator
import org.gradle.configuration.project.PluginsProjectConfigureActions
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.Environment
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.initialization.VintageBuildModelController
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildModelController
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.IntermediateBuildActionRunner
import org.gradle.internal.cc.base.services.ProjectRefResolver
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.internal.cc.impl.services.ConfigurationCacheEnvironment
import org.gradle.internal.cc.impl.services.DefaultEnvironment
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.extensions.core.get
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.CachingServiceLocator
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.invocation.DefaultGradle
import org.gradle.tooling.provider.model.internal.DefaultIntermediateToolingModelProvider
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import org.gradle.tooling.provider.model.internal.ToolingModelProjectDependencyListener


class DefaultBuildModelControllerServices(
    private val buildModelParameters: BuildModelParameters,
) : BuildModelControllerServices {
    override fun servicesForBuild(buildDefinition: BuildDefinition, owner: BuildState, parentBuild: BuildState?): BuildModelControllerServices.Supplier {
        return BuildModelControllerServices.Supplier { registration, buildScopeServices ->
            registration.add(BuildDefinition::class.java, buildDefinition)
            registration.add(BuildState::class.java, owner)
            registration.addProvider(ServicesProvider(buildDefinition, parentBuild, buildScopeServices))
            if (buildModelParameters.isConfigurationCache) {
                registration.addProvider(ConfigurationCacheBuildControllerProvider())
                registration.add(ConfigurationCacheEnvironment::class.java)
                registration.add(ProjectRefResolver::class.java)
            } else {
                registration.addProvider(VintageBuildControllerProvider())
                registration.add(Environment::class.java, DefaultEnvironment::class.java)
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
        private val buildScopeServices: ServiceRegistry
    ) : ServiceRegistrationProvider {
        @Provides
        fun createGradleModel(instantiator: Instantiator, serviceRegistryFactory: ServiceRegistryFactory): GradleInternal? {
            return instantiator.newInstance(
                DefaultGradle::class.java,
                parentBuild,
                buildDefinition.startParameter,
                serviceRegistryFactory
            )
        }

        @Provides
        fun createBuildLifecycleController(buildLifecycleControllerFactory: BuildLifecycleControllerFactory): BuildLifecycleController {
            return buildLifecycleControllerFactory.newInstance(buildDefinition, buildScopeServices)
        }

        @Provides
        fun createIntermediateToolingModelProvider(
            buildOperationExecutor: BuildOperationExecutor,
            buildModelParameters: BuildModelParameters,
            parameterCarrierFactory: ToolingModelParameterCarrier.Factory,
            listenerManager: ListenerManager
        ): IntermediateToolingModelProvider {
            val projectDependencyListener = listenerManager.getBroadcaster(ToolingModelProjectDependencyListener::class.java)
            val runner = IntermediateBuildActionRunner(buildOperationExecutor, buildModelParameters, "Tooling API intermediate model")
            return DefaultIntermediateToolingModelProvider(runner, parameterCarrierFactory, projectDependencyListener)
        }
    }

    private
    class ConfigurationCacheBuildControllerProvider : ServiceRegistrationProvider {
        @Provides
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
    class VintageBuildControllerProvider : ServiceRegistrationProvider {
        @Provides
        fun createBuildModelController(
            gradle: GradleInternal,
            stateTransitionControllerFactory: StateTransitionControllerFactory
        ): BuildModelController {
            val projectsPreparer: ProjectsPreparer = gradle.services.get()
            val settingsPreparer: SettingsPreparer = gradle.services.get()
            val taskExecutionPreparer: TaskExecutionPreparer = gradle.services.get()
            return VintageBuildModelController(gradle, projectsPreparer, settingsPreparer, taskExecutionPreparer, stateTransitionControllerFactory)
        }
    }

    private
    class ConfigurationCacheIsolatedProjectsProvider : ServiceRegistrationProvider {
        @Provides
        fun createCrossProjectModelAccess(
            projectRegistry: ProjectRegistry<ProjectInternal>,
            problemsListener: ProblemsListener,
            problemFactory: ProblemFactory,
            listenerManager: ListenerManager,
            dynamicCallProblemReporting: DynamicCallProblemReporting,
            buildModelParameters: BuildModelParameters,
            instantiator: Instantiator,
        ): CrossProjectModelAccess {
            val delegate = VintageIsolatedProjectsProvider().createCrossProjectModelAccess(projectRegistry)
            return ProblemReportingCrossProjectModelAccess(
                delegate,
                problemsListener,
                listenerManager.getBroadcaster(CoupledProjectsListener::class.java),
                problemFactory,
                dynamicCallProblemReporting,
                buildModelParameters,
                instantiator
            )
        }

        @Provides
        fun createDynamicCallProjectIsolationProblemReporting(dynamicCallContextTracker: DynamicCallContextTracker): DynamicCallProblemReporting =
            DefaultDynamicCallProblemReporting().also { reporting ->
                dynamicCallContextTracker.onEnter(reporting::enterDynamicCall)
                dynamicCallContextTracker.onLeave(reporting::leaveDynamicCall)
            }

        @Provides
        fun createDynamicLookupRoutine(
            dynamicCallContextTracker: DynamicCallContextTracker,
            buildModelParameters: BuildModelParameters
        ): DynamicLookupRoutine = when {
            buildModelParameters.isIsolatedProjects -> TrackingDynamicLookupRoutine(dynamicCallContextTracker)
            else -> DefaultDynamicLookupRoutine()
        }
    }

    private
    class VintageIsolatedProjectsProvider : ServiceRegistrationProvider {
        @Provides
        fun createCrossProjectModelAccess(
            projectRegistry: ProjectRegistry<ProjectInternal>
        ): CrossProjectModelAccess {
            return DefaultCrossProjectModelAccess(projectRegistry)
        }

        @Provides
        fun createDynamicLookupRoutine(): DynamicLookupRoutine =
            DefaultDynamicLookupRoutine()
    }

    private
    class ConfigurationCacheModelProvider : ServiceRegistrationProvider {
        @Provides
        fun createProjectEvaluator(
            buildOperationRunner: BuildOperationRunner,
            cachingServiceLocator: CachingServiceLocator,
            scriptPluginFactory: ScriptPluginFactory,
            fingerprintController: ConfigurationCacheFingerprintController,
            cancellationToken: BuildCancellationToken
        ): ProjectEvaluator {
            val evaluator = VintageModelProvider().createProjectEvaluator(buildOperationRunner, cachingServiceLocator, scriptPluginFactory, cancellationToken)
            return ConfigurationCacheAwareProjectEvaluator(evaluator, fingerprintController)
        }
    }

    private
    class VintageModelProvider : ServiceRegistrationProvider {
        @Provides
        fun createProjectEvaluator(
            buildOperationRunner: BuildOperationRunner,
            cachingServiceLocator: CachingServiceLocator,
            scriptPluginFactory: ScriptPluginFactory,
            cancellationToken: BuildCancellationToken
        ): ProjectEvaluator {
            val withActionsEvaluator = ConfigureActionsProjectEvaluator(
                PluginsProjectConfigureActions.from(cachingServiceLocator),
                BuildScriptProcessor(scriptPluginFactory),
                DelayedConfigurationActions()
            )
            return LifecycleProjectEvaluator(buildOperationRunner, withActionsEvaluator, cancellationToken)
        }
    }
}
