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

import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildModelParametersFactory
import org.gradle.internal.buildtree.control.DefaultBuildModelParametersFactory
import org.gradle.internal.cc.base.problems.IgnoringProblemsListener
import org.gradle.internal.cc.impl.problems.ConfigurationCacheProblems
import org.gradle.internal.cc.impl.promo.ConfigurationCachePromoHandler
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices


class BuildTreeControlServices : AbstractGradleModuleServices() {

    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BuildModelParametersFactory::class.java, DefaultBuildModelParametersFactory::class.java)
        }
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheRepository::class.java)
            add(ConfigurationCacheEntryCollector::class.java)
        }
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.addProvider(object : ServiceRegistrationProvider {
            @Provides
            fun configure(registration: ServiceRegistration, modelParameters: BuildModelParameters, requirements: BuildActionModelRequirements) {
                // Set up CC problem reporting pipeline and promo, based on the build configuration
                when {
                    // Collect and report problems. Don't suggest enabling CC if it is on, even if implicitly (e.g. enabled by isolated projects).
                    // Most likely, the user who tries IP is already aware of CC and nudging will be just noise.
                    modelParameters.isConfigurationCache -> registration.add(ConfigurationCacheProblems::class.java)
                    // Allow nudging to enable CC if it is off and there is no explicit decision. CC doesn't work for model building so do not nudge there.
                    !requirements.startParameter.configurationCache.isExplicit && !requirements.isCreatesModel -> registration.add(ConfigurationCachePromoHandler::class.java)
                    // Do not nudge if CC is explicitly disabled or if models are requested.
                    else -> registration.add(ProblemsListener::class.java, IgnoringProblemsListener)
                }

                registration.addProvider(
                    when {
                        modelParameters.isIsolatedProjects -> IsolatedProjectsGradleModeServices.BuildTree
                        modelParameters.isConfigurationCache -> ConfigurationCacheGradleModeServices.BuildTree
                        else -> VintageGradleModeServices.BuildTree
                    }
                )
            }
        })
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(object : ServiceRegistrationProvider {
            @Provides
            fun configure(registration: ServiceRegistration, modelParameters: BuildModelParameters) {
                registration.addProvider(
                    when {
                        modelParameters.isIsolatedProjects -> IsolatedProjectsGradleModeServices.Build
                        modelParameters.isConfigurationCache -> ConfigurationCacheGradleModeServices.Build
                        else -> VintageGradleModeServices.Build
                    }
                )
            }
        })
    }
}
