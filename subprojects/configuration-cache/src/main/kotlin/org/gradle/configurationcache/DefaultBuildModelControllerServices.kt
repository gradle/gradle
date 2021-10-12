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
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.project.BuildScriptProcessor
import org.gradle.configuration.project.ConfigureActionsProjectEvaluator
import org.gradle.configuration.project.DelayedConfigurationActions
import org.gradle.configuration.project.LifecycleProjectEvaluator
import org.gradle.configuration.project.PluginsProjectConfigureActions
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.CachingServiceLocator
import org.gradle.internal.service.scopes.BuildScopeServices
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.invocation.DefaultGradle


class DefaultBuildModelControllerServices : BuildModelControllerServices {
    override fun servicesForBuild(buildDefinition: BuildDefinition, owner: BuildState, parentBuild: BuildState?): BuildModelControllerServices.Supplier {
        return BuildModelControllerServices.Supplier { registration, services ->
            registration.add(BuildDefinition::class.java, buildDefinition)
            registration.add(BuildState::class.java, owner)
            registration.addProvider(ServicesProvider(buildDefinition, parentBuild, services))
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

        fun createProjectEvaluator(
            buildModelParameters: BuildModelParameters,
            buildOperationExecutor: BuildOperationExecutor,
            cachingServiceLocator: CachingServiceLocator,
            scriptPluginFactory: ScriptPluginFactory
        ): ProjectEvaluator {
            val withActionsEvaluator = ConfigureActionsProjectEvaluator(
                PluginsProjectConfigureActions.from(cachingServiceLocator),
                BuildScriptProcessor(scriptPluginFactory),
                DelayedConfigurationActions()
            )
            val evaluator = LifecycleProjectEvaluator(buildOperationExecutor, withActionsEvaluator)
            return if (buildModelParameters.isIsolatedProjects) {
                ConfigurationCacheAwareProjectEvaluator(evaluator)
            } else {
                evaluator
            }
        }
    }
}
