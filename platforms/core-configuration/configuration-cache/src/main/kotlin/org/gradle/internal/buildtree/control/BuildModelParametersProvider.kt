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

import org.gradle.api.GradleException
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.Logging
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildoption.InternalOption
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildoption.StringInternalOption
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildModelParameters

/**
 * Consolidates features requested by the user into flags controlling various Gradle behaviors.
 *
 * @see org.gradle.internal.buildtree.BuildModelParameters
 */
internal
object BuildModelParametersProvider {

    private
    val logger = Logging.getLogger(BuildModelParametersProvider::class.java)

    private
    val configurationCacheParallelStore = InternalFlag("org.gradle.configuration-cache.internal.parallel-store", true)

    private
    val configurationCacheParallelLoad = InternalFlag("org.gradle.configuration-cache.internal.parallel-load", true)

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
        InvocationScenarioParameter.Option("org.gradle.internal.isolated-projects.caching", InvocationScenarioParameter.NONE)

    private
    val resilientModelBuilding =
        InternalFlag("org.gradle.internal.resilient-model-building", false)

    /**
     * A public *system property* that allows removing the implication that
     * `org.gradle.parallel` also controls parallel model building for Vintage.
     *
     * It exists as a transitionary measure to allow IDEs to effectively require a separate user opt-in
     * into parallel model building via the explicit `org.gradle.tooling.parallel` property.
     */
    @JvmStatic
    val parallelModelBuildingIgnoreLegacyDefault = "org.gradle.tooling.parallel.ignore-legacy-default"

    /**
     * Determines Gradle features and behaviors that are required or requested by the build action.
     *
     * This includes features like Configuration Cache, Isolated Projects
     * and their sub-behaviors (e.g., caching and parallelism controls).
     *
     * @throws org.gradle.api.GradleException if the requirements are contradictory
     */
    @JvmStatic
    fun parameters(requirements: BuildActionModelRequirements, options: InternalOptions): BuildModelParameters {
        val startParameter = requirements.startParameter
        warnOnPreviouslyExistingOptions(options)

        val ccDisabledReason = getConfigurationCacheDisabledReason(startParameter)
        return when {
            // TODO:isolated should this also disable IP?
            ccDisabledReason != null -> vintageMode(requirements, startParameter, options, ccDisabledReason)
            startParameter.isolatedProjects.get() -> isolatedProjectsMode(requirements, startParameter, options)
            startParameter.configurationCache.get() ->
                if (requirements.isCreatesModel) {
                    // CC by itself does not yet support caching models or caching of the work graph that runs before model building
                    vintageMode(requirements, startParameter, options)
                } else {
                    configurationCacheTasksOnlyMode(requirements, startParameter, options)
                }

            else -> vintageMode(requirements, startParameter, options)
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
        return GradleVintageMode(
            modelBuilding = true,
            parallelProjectExecution = startParameter.isParallelProjectExecutionEnabled,
            configureOnDemand = startParameter.isConfigureOnDemand,
            configurationCacheDisabledReason = null,
            parallelModelBuilding = false,
            resilientModelBuilding = false
        )
    }

    private
    fun vintageMode(
        requirements: BuildActionModelRequirements,
        startParameter: StartParameterInternal,
        options: InternalOptions,
        ccDisabledReason: String? = null
    ): GradleVintageMode {

        val parallelProjectExecution = startParameter.isParallelProjectExecutionEnabled
        val parallelModelBuilding = parallelModelBuildingForVintage(startParameter)
        return if (requirements.isCreatesModel) {
            GradleVintageMode(
                modelBuilding = true,
                parallelProjectExecution = parallelProjectExecution || parallelModelBuilding,
                configureOnDemand = false,
                configurationCacheDisabledReason = ccDisabledReason,
                parallelModelBuilding = parallelModelBuilding,
                resilientModelBuilding = options[resilientModelBuilding],
            )
        } else {
            GradleVintageMode(
                modelBuilding = false,
                parallelProjectExecution = parallelProjectExecution,
                configureOnDemand = startParameter.isConfigureOnDemand,
                configurationCacheDisabledReason = ccDisabledReason,
                parallelModelBuilding = false,
                resilientModelBuilding = false,
            )
        }
    }

    private
    fun parallelModelBuildingForVintage(startParameter: StartParameterInternal): Boolean {
        val parallelModelBuildingOption = startParameter.parallelToolingModelBuilding
        val parallelModelBuildingIgnoreLegacyDefault =
            java.lang.Boolean.parseBoolean(startParameter.systemPropertiesArgs[parallelModelBuildingIgnoreLegacyDefault])
        val parallelModelBuilding = when {
            parallelModelBuildingOption.isExplicit -> parallelModelBuildingOption.get()
            parallelModelBuildingIgnoreLegacyDefault -> false
            else -> startParameter.isParallelProjectExecutionEnabled
        }
        return parallelModelBuilding
    }

    private
    fun configurationCacheTasksOnlyMode(
        requirements: BuildActionModelRequirements,
        startParameter: StartParameterInternal,
        options: InternalOptions
    ): GradleConfigurationCacheMode {

        return GradleConfigurationCacheMode(
            parallelProjectExecution = requirements.startParameter.isParallelProjectExecutionEnabled,
            configureOnDemand = startParameter.isConfigureOnDemand,
            configurationCacheParallelStore = startParameter.isConfigurationCacheParallel && options[configurationCacheParallelStore],
            configurationCacheParallelLoad = options[configurationCacheParallelLoad],
        )
    }

    private
    fun isolatedProjectsMode(
        requirements: BuildActionModelRequirements,
        startParameter: StartParameterInternal,
        options: InternalOptions
    ): GradleIsolatedProjectsMode {

        if (!startParameter.configurationCache.get() && startParameter.configurationCache.isExplicit) {
            throw GradleException("Configuration Cache cannot be disabled when Isolated Projects is enabled.")
        }
        validateIsolatedProjectsCachingOption(options)

        val configureOnDemand = isolatedProjectsConfigureOnDemand.forInvocation(requirements, options)
        val parallelIsolatedProjects = isolatedProjectsParallel.forInvocation(requirements, options)
        val parallelConfigurationCacheStore = parallelIsolatedProjects && options[configurationCacheParallelStore]
        val invalidateCoupledProjects = options[invalidateCoupledProjects]

        return if (requirements.isCreatesModel) {
            GradleIsolatedProjectsMode(
                modelBuilding = true,
                parallelProjectExecution = parallelIsolatedProjects,
                configureOnDemand = configureOnDemand,
                configurationCacheParallelStore = parallelConfigurationCacheStore,
                parallelProjectConfiguration = parallelIsolatedProjects,
                cachingModelBuilding = options[isolatedProjectsCaching].buildingModels,
                parallelModelBuilding = parallelIsolatedProjects,
                invalidateCoupledProjects = invalidateCoupledProjects,
                modelAsProjectDependency = options[modelProjectDependencies],
                resilientModelBuilding = options[resilientModelBuilding]
            )
        } else {
            GradleIsolatedProjectsMode(
                modelBuilding = false,
                parallelProjectExecution = parallelIsolatedProjects,
                configureOnDemand = configureOnDemand,
                configurationCacheParallelStore = parallelConfigurationCacheStore,
                parallelProjectConfiguration = parallelIsolatedProjects,
                cachingModelBuilding = false,
                parallelModelBuilding = false,
                invalidateCoupledProjects = invalidateCoupledProjects,
                modelAsProjectDependency = false,
                resilientModelBuilding = false
            )
        }
    }

    private
    fun getConfigurationCacheDisabledReason(startParameter: StartParameterInternal): String? {
        val affectingOption = when {
            startParameter.writeDependencyVerifications.isNotEmpty() -> StartParameterBuildOptions.DependencyVerificationWriteOption.LONG_OPTION
            startParameter.isExportKeys -> StartParameterBuildOptions.ExportKeysOption.LONG_OPTION
            // Disable configuration cache when generating a property upgrade report, since report is generated during configuration phase, and we currently don't reference it in cc cache
            startParameter.isPropertyUpgradeReportEnabled -> StartParameterBuildOptions.PropertyUpgradeReportOption.LONG_OPTION
            else -> null
        }

        return affectingOption?.let { "due to --$it" }
    }

    private
    fun warnOnPreviouslyExistingOptions(options: InternalOptions) {
        val replacements = mapOf(
            "org.gradle.internal.isolated-projects.configure-on-demand.tooling" to isolatedProjectsConfigureOnDemand.propertyName,
            "org.gradle.internal.isolated-projects.configure-on-demand.tasks" to isolatedProjectsConfigureOnDemand.propertyName,
        )
        for ((previous, current) in replacements) {
            if (options.getOption(StringInternalOption.of(previous)).isExplicit) {
                logger.warn("Warning: option '$previous' has been replaced with '$current'")
            }
        }
    }

    private
    fun validateIsolatedProjectsCachingOption(options: InternalOptions) {
        val param = options[isolatedProjectsCaching]
        val supported = listOf(InvocationScenarioParameter.TOOLING, InvocationScenarioParameter.NONE)
        require(param in supported) {
            "Unsupported value for '%s' option: %s. Supported values: %s".format(
                isolatedProjectsCaching.propertyName, param.value, supported.map { it.value })
        }
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
    operator fun <T : Any> InternalOptions.get(option: InternalOption<T>): T = getOption(option).get()
}
