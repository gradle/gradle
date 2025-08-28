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

package org.gradle.internal.buildtree

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.cc.base.logger


/**
 * Consolidates features requested by the user into flags controlling various Gradle behaviors.
 *
 * @see BuildModelParameters
 */
internal
object BuildModelParametersProvider {

    private
    val parallelBuilding = InternalFlag("org.gradle.internal.tooling.parallel", true)

    private
    val invalidateCoupledProjects = InternalFlag("org.gradle.internal.invalidate-coupled-projects", true)

    /**
     * If model dependencies between projects should be treated as project dependencies.
     * Model dependency is observed when a project requests a model from another project.
     */
    private
    val modelProjectDependencies = InternalFlag("org.gradle.internal.model-project-dependencies", true)

    private
    val isolatedProjectsToolingModelsConfigureOnDemand =
        InternalFlag("org.gradle.internal.isolated-projects.configure-on-demand.tooling", false)

    private
    val isolatedProjectsTasksConfigureOnDemand =
        InternalFlag("org.gradle.internal.isolated-projects.configure-on-demand.tasks", false)

    // Experimental flag to enable resilient model building.
    private
    val resilientModelBuilding =
        InternalFlag("org.gradle.internal.resilient-model-building", false)

    fun parameters(
        requirements: BuildActionModelRequirements,
        startParameter: StartParameterInternal,
        configurationCacheLogLevel: LogLevel
    ): BuildModelParameters {

        val options = DefaultInternalOptions(startParameter.systemPropertiesArgs)
        val requiresTasks = requirements.isRunsTasks
        val isolatedProjects = startParameter.isolatedProjects.get()
        val parallelProjectExecution = isolatedProjects || requirements.startParameter.isParallelProjectExecutionEnabled
        val parallelToolingActions = parallelProjectExecution && options.getOption(parallelBuilding).get()
        val invalidateCoupledProjects = isolatedProjects && options.getOption(invalidateCoupledProjects).get()
        val modelAsProjectDependency = isolatedProjects && options.getOption(modelProjectDependencies).get()
        val resilientModelBuilding = options.getOption(resilientModelBuilding).get()

        return if (requirements.isCreatesModel) {
            val configureOnDemand = isolatedProjects &&
                options.getOption(isolatedProjectsToolingModelsConfigureOnDemand).get() &&
                (!requiresTasks || options.getOption(isolatedProjectsTasksConfigureOnDemand).get())
            DefaultBuildModelParameters(
                requiresToolingModels = true,
                parallelProjectExecution = parallelProjectExecution,
                configureOnDemand = configureOnDemand,
                configurationCache = isolatedProjects,
                isolatedProjects = isolatedProjects,
                intermediateModelCache = isolatedProjects,
                parallelToolingApiActions = parallelToolingActions,
                invalidateCoupledProjects = invalidateCoupledProjects,
                modelAsProjectDependency = modelAsProjectDependency,
                resilientModelBuilding = resilientModelBuilding
            )
        } else {
            val configurationCache = isolatedProjects || startParameter.configurationCache.get()
            val configureOnDemand =
                if (isolatedProjects) options.getOption(isolatedProjectsTasksConfigureOnDemand).get()
                else startParameter.isConfigureOnDemand

            fun disabledConfigurationCacheBuildModelParameters(buildOptionReason: String): BuildModelParameters {
                logger.log(configurationCacheLogLevel, "{} as configuration cache cannot be reused due to --{}", requirements.actionDisplayName.capitalizedDisplayName, buildOptionReason)
                return DefaultBuildModelParameters(
                    requiresToolingModels = false,
                    parallelProjectExecution = parallelProjectExecution,
                    configureOnDemand = configureOnDemand,
                    configurationCache = false,
                    isolatedProjects = false,
                    intermediateModelCache = false,
                    parallelToolingApiActions = parallelToolingActions,
                    invalidateCoupledProjects = invalidateCoupledProjects,
                    modelAsProjectDependency = modelAsProjectDependency,
                    resilientModelBuilding = resilientModelBuilding
                )
            }

            when {
                configurationCache && startParameter.writeDependencyVerifications.isNotEmpty() -> disabledConfigurationCacheBuildModelParameters(StartParameterBuildOptions.DependencyVerificationWriteOption.LONG_OPTION)
                configurationCache && startParameter.isExportKeys -> disabledConfigurationCacheBuildModelParameters(StartParameterBuildOptions.ExportKeysOption.LONG_OPTION)
                // Disable configuration cache when generating a property upgrade report, since report is generated during configuration phase, and we currently don't reference it in cc cache
                configurationCache && startParameter.isPropertyUpgradeReportEnabled -> disabledConfigurationCacheBuildModelParameters(StartParameterBuildOptions.PropertyUpgradeReportOption.LONG_OPTION)
                else -> DefaultBuildModelParameters(
                    requiresToolingModels = false,
                    parallelProjectExecution = parallelProjectExecution,
                    configureOnDemand = configureOnDemand,
                    configurationCache = configurationCache,
                    isolatedProjects = isolatedProjects,
                    intermediateModelCache = false,
                    parallelToolingApiActions = parallelToolingActions,
                    invalidateCoupledProjects = invalidateCoupledProjects,
                    modelAsProjectDependency = modelAsProjectDependency,
                    resilientModelBuilding = resilientModelBuilding
                )
            }
        }
    }

    /**
     * Compute parameters for nested build trees, which are created by the `GradleBuild` tasks.
     *
     * Many features are not supported for nested build trees, such as Configuration Cache or Isolated Projects.
     *
     * @see org.gradle.internal.build.NestedRootBuildRunner
     */
    fun parametersForNestedBuildTree(startParameter: StartParameterInternal): BuildModelParameters {
        return DefaultBuildModelParameters(
            requiresToolingModels = true,
            parallelProjectExecution = startParameter.isParallelProjectExecutionEnabled,
            configureOnDemand = startParameter.isConfigureOnDemand,
            configurationCache = false,
            isolatedProjects = false,
            intermediateModelCache = false,
            parallelToolingApiActions = false,
            invalidateCoupledProjects = false,
            modelAsProjectDependency = false,
            resilientModelBuilding = false
        )
    }
}
