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

import org.gradle.StartParameter
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.extensions.core.get
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.operations.BuildOperationExecutor


class ConfigurationCacheBuildTreeLifecycleControllerFactory(
    private val buildModelParameters: BuildModelParameters,
    buildOperationExecutor: BuildOperationExecutor,
    private val cache: BuildTreeConfigurationCache,
    private val taskGraph: BuildTreeWorkGraphController,
    private val stateTransitionControllerFactory: StateTransitionControllerFactory,
    private val startParameter: StartParameter,
    private val configurationCacheStartParameter: ConfigurationCacheStartParameter,
    private val buildStateRegistry: BuildStateRegistry,
) : BuildTreeLifecycleControllerFactory {
    private
    val vintageFactory = VintageBuildTreeLifecycleControllerFactory(buildModelParameters, taskGraph, buildOperationExecutor, stateTransitionControllerFactory, startParameter)

    override fun createRootBuildController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        // Some temporary wiring: the cache implementation is still scoped to the root build rather than the build tree
        cache.attachRootBuild(targetBuild.gradle.services.get())

        cache.initializeCacheEntry()

        // Currently, apply the decoration only to the root build, as the cache implementation is still scoped to the root build (that is, it assumes it is only applied to the root build)
        return createController(true, targetBuild, workExecutor, finishExecutor)
    }

    override fun createController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        return createController(false, targetBuild, workExecutor, finishExecutor)
    }

    private
    fun createController(applyCaching: Boolean, targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        if (!applyCaching) {
            return vintageFactory.createController(targetBuild, workExecutor, finishExecutor)
        }

        val workPreparer = vintageFactory.createWorkPreparer(targetBuild)
        val workController = ConfigurationCacheAwareBuildTreeWorkController(workPreparer, workExecutor, taskGraph, cache, buildStateRegistry, configurationCacheStartParameter)

        val defaultModelCreator = vintageFactory.createModelCreator(targetBuild)
        val modelCreator = ConfigurationCacheAwareBuildTreeModelCreator(defaultModelCreator, cache)

        val finisher = ConfigurationCacheAwareFinishExecutor(finishExecutor, cache)

        return DefaultBuildTreeLifecycleController(targetBuild, workController, modelCreator, finisher, stateTransitionControllerFactory, startParameter, buildModelParameters)
    }
}
