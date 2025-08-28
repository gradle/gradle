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

import org.gradle.internal.buildtree.BuildModelParameters

internal
data class DefaultBuildModelParameters(
    private val requiresToolingModels: Boolean,
    private val parallelProjectExecution: Boolean,
    private val configureOnDemand: Boolean,
    private val configurationCache: Boolean,
    private val configurationCacheParallelStore: Boolean,
    private val configurationCacheParallelLoad: Boolean,
    private val isolatedProjects: Boolean,
    private val parallelProjectConfiguration: Boolean,
    private val intermediateModelCache: Boolean,
    private val parallelToolingApiActions: Boolean,
    private val invalidateCoupledProjects: Boolean,
    private val modelAsProjectDependency: Boolean,
    private val resilientModelBuilding: Boolean
) : BuildModelParameters {

    override fun isRequiresToolingModels(): Boolean = requiresToolingModels

    override fun isParallelProjectExecution(): Boolean = parallelProjectExecution

    override fun isConfigureOnDemand(): Boolean = configureOnDemand

    override fun isConfigurationCache(): Boolean = configurationCache

    override fun isConfigurationCacheParallelStore(): Boolean = configurationCacheParallelStore

    override fun isConfigurationCacheParallelLoad(): Boolean = configurationCacheParallelLoad

    override fun isIsolatedProjects(): Boolean = isolatedProjects

    override fun isParallelProjectConfiguration(): Boolean = parallelProjectConfiguration

    override fun isIntermediateModelCache(): Boolean = intermediateModelCache

    override fun isParallelToolingApiActions(): Boolean = parallelToolingApiActions

    override fun isInvalidateCoupledProjects(): Boolean = invalidateCoupledProjects

    override fun isModelAsProjectDependency(): Boolean = modelAsProjectDependency

    override fun isResilientModelBuilding(): Boolean = resilientModelBuilding

    override fun toDisplayMap(): Map<String, Boolean> = mapOf(
        "requiresToolingModels" to requiresToolingModels,
        "parallelProjectExecution" to parallelProjectExecution,
        "configureOnDemand" to configureOnDemand,
        "configurationCache" to configurationCache,
        "configurationCacheParallelStore" to configurationCacheParallelStore,
        "configurationCacheParallelLoad" to configurationCacheParallelLoad,
        "isolatedProjects" to isolatedProjects,
        "parallelProjectConfiguration" to parallelProjectConfiguration,
        "intermediateModelCache" to intermediateModelCache,
        "parallelToolingApiActions" to parallelToolingApiActions,
        "invalidateCoupledProjects" to invalidateCoupledProjects,
        "modelAsProjectDependency" to modelAsProjectDependency,
        "resilientModelBuilding" to resilientModelBuilding
    )
}
