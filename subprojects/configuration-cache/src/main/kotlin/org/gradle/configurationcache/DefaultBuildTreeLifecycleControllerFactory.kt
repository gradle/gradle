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

import org.gradle.composite.internal.IncludedBuildTaskGraph
import org.gradle.configurationcache.extensions.get
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController
import org.gradle.internal.buildtree.DefaultBuildTreeModelCreator
import org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer


class DefaultBuildTreeLifecycleControllerFactory(
    private val startParameter: ConfigurationCacheStartParameter,
    private val cache: BuildTreeConfigurationCache,
    private val taskGraph: IncludedBuildTaskGraph,
) : BuildTreeLifecycleControllerFactory {
    override fun createController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        // Currently, apply the decoration only to the root build, as the cache implementation is still scoped to the root build
        // (that is, it assumes it is only applied to the root build)
        val rootBuild = targetBuild.gradle.isRootBuild

        val defaultWorkPreparer = DefaultBuildTreeWorkPreparer(targetBuild, taskGraph)
        val workPreparer = if (startParameter.isEnabled && rootBuild) {
            ConfigurationCacheAwareBuildTreeWorkPreparer(defaultWorkPreparer, cache)
        } else {
            defaultWorkPreparer
        }

        val defaultModelCreator = DefaultBuildTreeModelCreator(targetBuild)
        val modelCreator = if (startParameter.isEnabled && rootBuild) {
            ConfigurationCacheAwareBuildTreeModelCreator(defaultModelCreator, cache)
        } else {
            defaultModelCreator
        }

        // Some temporary wiring: the cache implementation is still scoped to the root build rather than the build tree
        if (startParameter.isEnabled && rootBuild) {
            cache.attachRootBuild(targetBuild.gradle.services.get())
        }

        return DefaultBuildTreeLifecycleController(targetBuild, taskGraph, workPreparer, workExecutor, modelCreator, finishExecutor)
    }
}
