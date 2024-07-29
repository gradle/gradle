/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.cc.impl.services

import org.gradle.internal.buildtree.BuildModelParameters


internal
data class DefaultBuildModelParameters(
    private val parallelProjectExecution: Boolean,
    private val configureOnDemand: Boolean,
    private val configurationCache: Boolean,
    private val isolatedProjects: Boolean,
    private val requiresBuildModel: Boolean,
    private val intermediateModelCache: Boolean,
    private val parallelToolingApiActions: Boolean,
    private val invalidateCoupledProjects: Boolean,
    private val modelAsProjectDependency: Boolean
) : BuildModelParameters {

    override fun isParallelProjectExecution(): Boolean = parallelProjectExecution

    override fun isRequiresBuildModel(): Boolean = requiresBuildModel

    override fun isConfigureOnDemand(): Boolean = configureOnDemand

    override fun isConfigurationCache(): Boolean = configurationCache

    override fun isIsolatedProjects(): Boolean = isolatedProjects

    override fun isIntermediateModelCache(): Boolean = intermediateModelCache

    override fun isParallelToolingApiActions(): Boolean = parallelToolingApiActions

    override fun isInvalidateCoupledProjects(): Boolean = invalidateCoupledProjects

    override fun isModelAsProjectDependency(): Boolean = modelAsProjectDependency
}
