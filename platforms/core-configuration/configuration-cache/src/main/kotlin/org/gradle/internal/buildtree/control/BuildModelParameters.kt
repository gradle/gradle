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

import org.gradle.internal.buildtree.BuildModelParameters


internal sealed class AbstractBuildModelParameters : BuildModelParameters {

    override fun toDisplayMap(): Map<String, Any?> = mapOf(
        "cachingModelBuilding" to isCachingModelBuilding,
        "configurationCache" to isConfigurationCache,
        "configurationCacheDisabledReason" to configurationCacheDisabledReason,
        "configurationCacheParallelLoad" to isConfigurationCacheParallelLoad,
        "configurationCacheParallelStore" to isConfigurationCacheParallelStore,
        "configureOnDemand" to isConfigureOnDemand,
        "invalidateCoupledProjects" to isInvalidateCoupledProjects,
        "isolatedProjects" to isIsolatedProjects,
        "modelAsProjectDependency" to isModelAsProjectDependency,
        "modelBuilding" to isModelBuilding,
        "parallelModelBuilding" to isParallelModelBuilding,
        "parallelProjectConfiguration" to isParallelProjectConfiguration,
        "parallelProjectExecution" to isParallelProjectExecution,
        "resilientModelBuilding" to isResilientModelBuilding,
    )
}

internal class GradleVintageMode(
    private val modelBuilding: Boolean,
    private val parallelProjectExecution: Boolean,
    private val configureOnDemand: Boolean,
    private val configurationCacheDisabledReason: String?,
    private val parallelModelBuilding: Boolean,
    private val resilientModelBuilding: Boolean,
) : AbstractBuildModelParameters() {

    override fun isParallelProjectExecution(): Boolean = parallelProjectExecution

    override fun isConfigureOnDemand(): Boolean = configureOnDemand

    override fun isConfigurationCache(): Boolean = false
    override fun getConfigurationCacheDisabledReason(): String? = configurationCacheDisabledReason
    override fun isConfigurationCacheParallelStore(): Boolean = false
    override fun isConfigurationCacheParallelLoad(): Boolean = false

    override fun isIsolatedProjects(): Boolean = false
    override fun isParallelProjectConfiguration(): Boolean = false
    override fun isInvalidateCoupledProjects(): Boolean = false
    override fun isModelAsProjectDependency(): Boolean = false

    override fun isModelBuilding(): Boolean = modelBuilding
    override fun isParallelModelBuilding(): Boolean = parallelModelBuilding
    override fun isCachingModelBuilding(): Boolean = false
    override fun isResilientModelBuilding(): Boolean = resilientModelBuilding

    override fun toString(): String = "GradleVintageMode"
}

internal class GradleConfigurationCacheMode(
    private val parallelProjectExecution: Boolean,
    private val configureOnDemand: Boolean,
    private val configurationCacheParallelStore: Boolean,
    private val configurationCacheParallelLoad: Boolean,
) : AbstractBuildModelParameters() {

    override fun isParallelProjectExecution(): Boolean = parallelProjectExecution

    override fun isConfigureOnDemand(): Boolean = configureOnDemand

    override fun isConfigurationCache(): Boolean = true
    override fun getConfigurationCacheDisabledReason(): String? = null
    override fun isConfigurationCacheParallelStore(): Boolean = configurationCacheParallelStore
    override fun isConfigurationCacheParallelLoad(): Boolean = configurationCacheParallelLoad

    override fun isIsolatedProjects(): Boolean = false
    override fun isParallelProjectConfiguration(): Boolean = false
    override fun isInvalidateCoupledProjects(): Boolean = false
    override fun isModelAsProjectDependency(): Boolean = false

    override fun isModelBuilding(): Boolean = false
    override fun isParallelModelBuilding(): Boolean = false
    override fun isCachingModelBuilding(): Boolean = false
    override fun isResilientModelBuilding(): Boolean = false

    override fun toString(): String = "GradleConfigurationCacheMode"
}

internal class GradleIsolatedProjectsMode(
    private val modelBuilding: Boolean,
    private val parallelProjectExecution: Boolean,
    private val configureOnDemand: Boolean,
    private val configurationCacheParallelStore: Boolean,
    private val parallelProjectConfiguration: Boolean,
    private val cachingModelBuilding: Boolean,
    private val parallelModelBuilding: Boolean,
    private val invalidateCoupledProjects: Boolean,
    private val modelAsProjectDependency: Boolean,
    private val resilientModelBuilding: Boolean
) : AbstractBuildModelParameters() {

    override fun isParallelProjectExecution(): Boolean = parallelProjectExecution

    override fun isConfigureOnDemand(): Boolean = configureOnDemand

    override fun isConfigurationCache(): Boolean = true
    override fun getConfigurationCacheDisabledReason(): String? = null
    override fun isConfigurationCacheParallelStore(): Boolean = configurationCacheParallelStore
    override fun isConfigurationCacheParallelLoad(): Boolean = true

    override fun isIsolatedProjects(): Boolean = true
    override fun isParallelProjectConfiguration(): Boolean = parallelProjectConfiguration
    override fun isInvalidateCoupledProjects(): Boolean = invalidateCoupledProjects
    override fun isModelAsProjectDependency(): Boolean = modelAsProjectDependency

    override fun isModelBuilding(): Boolean = modelBuilding
    override fun isParallelModelBuilding(): Boolean = parallelModelBuilding
    override fun isCachingModelBuilding(): Boolean = cachingModelBuilding
    override fun isResilientModelBuilding(): Boolean = resilientModelBuilding

    override fun toString(): String = "GradleIsolatedProjectsMode"
}

