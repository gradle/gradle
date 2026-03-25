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

import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.DefaultCrossProjectModelAccess
import org.gradle.api.internal.project.DefaultDynamicLookupRoutine
import org.gradle.api.internal.project.DynamicLookupRoutine
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.internal.DefaultDynamicCallContextTracker
import org.gradle.configuration.internal.DynamicCallContextTracker
import org.gradle.configuration.project.BuildScriptProcessor
import org.gradle.configuration.project.ConfigureActionsProjectEvaluator
import org.gradle.configuration.project.DelayedConfigurationActions
import org.gradle.configuration.project.LifecycleProjectEvaluator
import org.gradle.configuration.project.PluginsProjectConfigureActions
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.VintageBuildModelController
import org.gradle.internal.build.BuildModelController
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.cc.base.services.ProjectRefResolver
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.service.CachingServiceLocator
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider


internal object BuildModelControllerServices : ServiceRegistrationProvider {

    @Provides
    fun configure(registration: ServiceRegistration, buildModelParameters: BuildModelParameters) {
        if (buildModelParameters.isVintage) {
            // region ALL MODES
            registration.add(BuildModelController::class.java, VintageBuildModelController::class.java)
            registration.add(CrossProjectModelAccess::class.java, DefaultCrossProjectModelAccess::class.java)
            registration.add(DynamicLookupRoutine::class.java, DefaultDynamicLookupRoutine::class.java)
            // endregion
        } else if (buildModelParameters.isConfigurationCache) {
            // region ALL MODES
            registration.add(BuildModelController::class.java, ConfigurationCacheAwareBuildModelController::class.java)
            // endregion

            // region CC and IP
            registration.add(ProjectRefResolver::class.java)

            if (!buildModelParameters.isIsolatedProjects) {
                registration.add(CrossProjectModelAccess::class.java, DefaultCrossProjectModelAccess::class.java)
                registration.add(DynamicLookupRoutine::class.java, DefaultDynamicLookupRoutine::class.java)
            } else { // IP
                registration.add(CrossProjectModelAccess::class.java, ProblemReportingCrossProjectModelAccess::class.java)
                registration.add(DynamicLookupRoutine::class.java, TrackingDynamicLookupRoutine::class.java)
                registration.add(DynamicCallContextTracker::class.java, DefaultDynamicCallContextTracker::class.java)
            }
            // endregion
        } else error("no other modes are supported")

        if (buildModelParameters.isCachingModelBuilding) {
            registration.addProvider(ConfigurationCacheModelProvider())
        } else {
            registration.addProvider(VintageModelProvider())
        }
    }

    // region IP services which are not instantiated unless IP is enabled

    @Provides
    fun createDynamicCallProjectIsolationProblemReporting(dynamicCallContextTracker: DynamicCallContextTracker): DynamicCallProblemReporting =
        DefaultDynamicCallProblemReporting().also { reporting ->
            dynamicCallContextTracker.onEnter(reporting::enterDynamicCall)
            dynamicCallContextTracker.onLeave(reporting::leaveDynamicCall)
        }

    // endregion

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
