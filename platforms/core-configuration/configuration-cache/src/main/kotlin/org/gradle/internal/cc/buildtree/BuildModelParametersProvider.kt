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

package org.gradle.internal.cc.buildtree

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildoption.InternalOption
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildoption.StringInternalOption
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.cc.base.logger
import kotlin.collections.iterator


/**
 * Consolidates features requested by the user into flags controlling various Gradle behaviors.
 *
 * @see org.gradle.internal.buildtree.BuildModelParameters
 */
internal
object BuildModelParametersProvider {

    private
    val configurationCacheParallelStore = InternalFlag("org.gradle.configuration-cache.internal.parallel-store", true)

    private
    val configurationCacheParallelLoad = InternalFlag("org.gradle.configuration-cache.internal.parallel-load", true)

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

    @JvmStatic
    val isolatedProjectsConfigureOnDemand =
        InvocationScenarioParameter.Option("org.gradle.internal.isolated-projects.configure-on-demand", InvocationScenarioParameter.NONE)

    @JvmStatic
    val isolatedProjectsParallel =
        InvocationScenarioParameter.Option("org.gradle.internal.isolated-projects.parallel", InvocationScenarioParameter.ANY)

    @JvmStatic
    val isolatedProjectsCaching =
        InvocationScenarioParameter.Option("org.gradle.internal.isolated-projects.caching", InvocationScenarioParameter.TOOLING)

    private
    val resilientModelBuilding =
        InternalFlag("org.gradle.internal.resilient-model-building", false)

    @JvmStatic
    fun parameters(
        requirements: BuildActionModelRequirements,
        startParameter: StartParameterInternal,
        configurationCacheLogLevel: LogLevel
    ): BuildModelParameters {

        val options = DefaultInternalOptions(startParameter.systemPropertiesArgs)
        warnOnPreviouslyExistingOptions(options)
        val requiresModels = requirements.isCreatesModel

        val isolatedProjects = startParameter.isolatedProjects.get()
        val configureOnDemand =
            if (isolatedProjects) isolatedProjectsConfigureOnDemand.forInvocation(requirements, options)
            else !requiresModels && startParameter.isConfigureOnDemand

        // --parallel
        val vintageParallel = requirements.startParameter.isParallelProjectExecutionEnabled
        val parallelIsolatedProjectsAllowed = isolatedProjectsParallel.forInvocation(requirements, options)
        val parallelProjectConfiguration = isolatedProjects && parallelIsolatedProjectsAllowed
        val parallelProjectExecution =
            if (isolatedProjects) parallelIsolatedProjectsAllowed
            else vintageParallel

        // Isolated projects without parallelism must be safe, so we ignore the Parallel CC flag
        val parallelConfigurationCacheStore =
            if (isolatedProjects) parallelIsolatedProjectsAllowed && options[configurationCacheParallelStore]
            else startParameter.isConfigurationCacheParallel && options[configurationCacheParallelStore]
        // Parallel load is always safe, as opposed to parallel store
        val parallelConfigurationCacheLoad = options[configurationCacheParallelLoad]

        validateIsolatedProjectsCachingOption(options)

        val parallelToolingActions = parallelProjectExecution && options[parallelBuilding]
        val invalidateCoupledProjects = isolatedProjects && options[invalidateCoupledProjects]
        val modelAsProjectDependency = isolatedProjects && options[modelProjectDependencies]
        val resilientModelBuilding = options[resilientModelBuilding]

        return if (requiresModels) {
            DefaultBuildModelParameters(
                requiresToolingModels = true,
                parallelProjectExecution = parallelProjectExecution,
                configureOnDemand = configureOnDemand,
                configurationCache = isolatedProjects,
                configurationCacheParallelStore = parallelConfigurationCacheStore,
                configurationCacheParallelLoad = parallelConfigurationCacheLoad,
                isolatedProjects = isolatedProjects,
                parallelProjectConfiguration = parallelProjectConfiguration,
                intermediateModelCache = isolatedProjects && options[isolatedProjectsCaching].buildingModels,
                parallelToolingApiActions = parallelToolingActions,
                invalidateCoupledProjects = invalidateCoupledProjects,
                modelAsProjectDependency = modelAsProjectDependency,
                resilientModelBuilding = resilientModelBuilding
            )
        } else {
            val configurationCache = isolatedProjects || startParameter.configurationCache.get()

            fun disabledConfigurationCacheBuildModelParameters(buildOptionReason: String): BuildModelParameters {
                logger.log(configurationCacheLogLevel, "{} as configuration cache cannot be reused due to --{}", requirements.actionDisplayName.capitalizedDisplayName, buildOptionReason)
                return DefaultBuildModelParameters(
                    requiresToolingModels = false,
                    parallelProjectExecution = parallelProjectExecution,
                    configureOnDemand = configureOnDemand,
                    configurationCache = false,
                    configurationCacheParallelStore = false,
                    configurationCacheParallelLoad = false,
                    isolatedProjects = false,
                    parallelProjectConfiguration = parallelProjectConfiguration,
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
                    configurationCacheParallelStore = parallelConfigurationCacheStore,
                    configurationCacheParallelLoad = parallelConfigurationCacheLoad,
                    isolatedProjects = isolatedProjects,
                    parallelProjectConfiguration = parallelProjectConfiguration,
                    intermediateModelCache = false,
                    parallelToolingApiActions = parallelToolingActions,
                    invalidateCoupledProjects = invalidateCoupledProjects,
                    modelAsProjectDependency = modelAsProjectDependency,
                    resilientModelBuilding = resilientModelBuilding
                )
            }
        }
    }

    private
    fun warnOnPreviouslyExistingOptions(options: InternalOptions) {
        val replacements = mapOf(
            "org.gradle.internal.isolated-projects.configure-on-demand.tooling" to isolatedProjectsConfigureOnDemand.systemPropertyName,
            "org.gradle.internal.isolated-projects.configure-on-demand.tasks" to isolatedProjectsConfigureOnDemand.systemPropertyName,
        )
        for ((previous, current) in replacements) {
            if (options.getOption(StringInternalOption(previous, null)).isExplicit) {
                logger.warn("Warning: option '$previous' has been replaced with '$current'")
            }
        }
    }

    private
    fun validateIsolatedProjectsCachingOption(options: DefaultInternalOptions) {
        val param = options[isolatedProjectsCaching]
        val supported = listOf(InvocationScenarioParameter.TOOLING, InvocationScenarioParameter.NONE)
        require(param in supported) {
            "Unsupported value for '%s' option: %s. Supported values: %s".format(
                isolatedProjectsCaching.systemPropertyName, param.value, supported.map { it.value })
        }
    }

    /**
     * Compute parameters for nested build trees, which are created by the `GradleBuild` tasks.
     *
     * Many features are not supported for nested build trees, such as Configuration Cache or Isolated Projects.
     *
     * @see org.gradle.internal.build.NestedRootBuildRunner
     */
    @JvmStatic
    fun parametersForNestedBuildTree(startParameter: StartParameterInternal): BuildModelParameters {
        return DefaultBuildModelParameters(
            requiresToolingModels = true,
            parallelProjectExecution = startParameter.isParallelProjectExecutionEnabled,
            configureOnDemand = startParameter.isConfigureOnDemand,
            configurationCache = false,
            configurationCacheParallelStore = false,
            configurationCacheParallelLoad = false,
            isolatedProjects = false,
            parallelProjectConfiguration = false,
            intermediateModelCache = false,
            parallelToolingApiActions = false,
            invalidateCoupledProjects = false,
            modelAsProjectDependency = false,
            resilientModelBuilding = false
        )
    }

    private
    fun InvocationScenarioParameter.Option.forInvocation(
        requirements: BuildActionModelRequirements,
        options: InternalOptions
    ): Boolean {
        val value = options[this]
        return (!requirements.isCreatesModel || value.buildingModels) &&
            (!requirements.isRunsTasks || value.runningTasks)
    }

    private
    operator fun <T> InternalOptions.get(option: InternalOption<T>): T = getOption(option).get()
}
