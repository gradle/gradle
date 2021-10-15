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

import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.configurationcache.extensions.get
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController
import org.gradle.internal.buildtree.DefaultBuildTreeModelCreator
import org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.resources.ProjectLeaseRegistry


class DefaultBuildTreeLifecycleControllerFactory(
    private val buildModelParameters: BuildModelParameters,
    private val taskGraph: BuildTreeWorkGraphController,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val projectLeaseRegistry: ProjectLeaseRegistry,
    private val stateTransitionControllerFactory: StateTransitionControllerFactory
) : BuildTreeLifecycleControllerFactory {
    override fun createRootBuildController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        // Currently, apply the decoration only to the root build, as the cache implementation is still scoped to the root build
        // (that is, it assumes it is only applied to the root build)
        return if (buildModelParameters.isConfigurationCache) {
            createController(true, targetBuild, workExecutor, finishExecutor)
        } else {
            createController(false, targetBuild, workExecutor, finishExecutor)
        }
    }

    override fun createController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        return createController(false, targetBuild, workExecutor, finishExecutor)
    }

    private
    fun createController(applyCaching: Boolean, targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        val defaultWorkPreparer = DefaultBuildTreeWorkPreparer(targetBuild.gradle.owner, targetBuild)
        val workPreparer = if (applyCaching) {
            // Only look this up if configuration caching is enabled, to avoid creating services
            val cache: BuildTreeConfigurationCache = targetBuild.gradle.services.get()
            ConfigurationCacheAwareBuildTreeWorkPreparer(defaultWorkPreparer, cache)
        } else {
            defaultWorkPreparer
        }

        val defaultModelCreator = DefaultBuildTreeModelCreator(buildModelParameters, targetBuild.gradle.owner, buildOperationExecutor, projectLeaseRegistry)
        val modelCreator = if (applyCaching) {
            // Only look this up if configuration caching is enabled, to avoid creating services
            val cache: BuildTreeConfigurationCache = targetBuild.gradle.services.get()
            ConfigurationCacheAwareBuildTreeModelCreator(defaultModelCreator, cache)
        } else {
            defaultModelCreator
        }

        val finisher = if (applyCaching) {
            // Only look this up if configuration caching is enabled, to avoid creating services
            val cache: BuildTreeConfigurationCache = targetBuild.gradle.services.get()
            ConfigurationCacheAwareFinishExecutor(finishExecutor, cache)
        } else {
            finishExecutor
        }

        // Some temporary wiring: the cache implementation is still scoped to the root build rather than the build tree
        if (applyCaching) {
            // Only look this up if configuration caching is enabled, to avoid creating services
            val cache: BuildTreeConfigurationCache = targetBuild.gradle.services.get()
            cache.attachRootBuild(targetBuild.gradle.services.get())
        }

        return DefaultBuildTreeLifecycleController(targetBuild, taskGraph, workPreparer, workExecutor, modelCreator, finisher, stateTransitionControllerFactory)
    }
}
