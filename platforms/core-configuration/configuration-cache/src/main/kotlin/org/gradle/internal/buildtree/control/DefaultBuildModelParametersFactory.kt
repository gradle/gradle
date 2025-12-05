/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.buildtree.control

import org.gradle.api.logging.Logging
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildModelParametersFactory
import org.gradle.util.internal.IncubationLogger

internal class DefaultBuildModelParametersFactory : BuildModelParametersFactory {

    private val logger = Logging.getLogger(DefaultBuildModelParametersFactory::class.java)

    override fun parametersForRootBuildTree(requirements: BuildActionModelRequirements, internalOptions: InternalOptions): BuildModelParameters {
        val modelParameters = BuildModelParametersProvider.parameters(requirements, internalOptions)
        logger.info("Operational build model parameters: {}", modelParameters.toDisplayMap())

        modelParameters.configurationCacheDisabledReason?.let { reason ->
            logger.lifecycle("{} as configuration cache cannot be reused {}", requirements.actionDisplayName.capitalizedDisplayName, reason)
        }

        if (modelParameters.isIsolatedProjects) {
            IncubationLogger.incubatingFeatureUsed("Isolated Projects")
        } else {
            if (modelParameters.isConfigurationCacheParallelStore) {
                IncubationLogger.incubatingFeatureUsed("Parallel Configuration Cache")
            }
            if (modelParameters.isConfigureOnDemand) {
                IncubationLogger.incubatingFeatureUsed("Configuration on demand")
            }
        }

        return modelParameters
    }

    override fun parametersForNestedBuildTree(requirements: BuildActionModelRequirements): BuildModelParameters {
        return BuildModelParametersProvider.parametersForNestedBuildTree(requirements.startParameter)
    }
}
