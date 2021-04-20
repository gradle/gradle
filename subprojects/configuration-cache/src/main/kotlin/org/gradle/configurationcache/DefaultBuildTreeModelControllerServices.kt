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

import org.gradle.api.GradleException
import org.gradle.api.internal.BuildType
import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildtree.BuildTreeModelControllerServices
import org.gradle.util.internal.IncubationLogger


class DefaultBuildTreeModelControllerServices : BuildTreeModelControllerServices {
    override fun servicesForBuildTree(runsTasks: Boolean, createsModel: Boolean, startParameter: StartParameterInternal): BuildTreeModelControllerServices.Supplier {
        // Isolated projects also implies configuration cache
        if (startParameter.isolatedProjects.get() && !startParameter.configurationCache.get()) {
            if(startParameter.configurationCache.isExplicit) {
                throw GradleException("The configuration cache cannot be disabled when isolated projects is enabled.")
            }
            startParameter.configurationCache = BuildOption.Value.defaultValue(true)
        }

        if (createsModel) {
            // When creating a model, disable certain features
            // Should not mutate the start parameter, but instead expose a new service to query the state of certain build model features
            startParameter.isConfigureOnDemand = false
            startParameter.configurationCache = BuildOption.Value.defaultValue(false)
            startParameter.isolatedProjects = BuildOption.Value.defaultValue(false)
        }

        if (!startParameter.isConfigurationCacheQuiet) {
            if (startParameter.isolatedProjects.get()) {
                IncubationLogger.incubatingFeatureUsed("Isolated projects")
            } else if (startParameter.configurationCache.get()) {
                IncubationLogger.incubatingFeatureUsed("Configuration cache")
            }
        }

        return BuildTreeModelControllerServices.Supplier { registration ->
            val buildType = if (runsTasks) BuildType.TASKS else BuildType.MODEL
            registration.add(BuildType::class.java, buildType)
        }
    }

    override fun servicesForNestedBuildTree(startParameter: StartParameterInternal): BuildTreeModelControllerServices.Supplier {
        // Configuration cache is not supported for nested build trees
        // Should not mutate the start parameter, but instead expose a new service to query the state of certain build model features
        startParameter.configurationCache = BuildOption.Value.defaultValue(false)
        startParameter.isolatedProjects = BuildOption.Value.defaultValue(false)
        return BuildTreeModelControllerServices.Supplier { registration -> registration.add(BuildType::class.java, BuildType.TASKS) }
    }
}
