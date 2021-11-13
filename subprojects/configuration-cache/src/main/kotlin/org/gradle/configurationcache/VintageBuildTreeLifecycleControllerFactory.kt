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


class VintageBuildTreeLifecycleControllerFactory(
    private val buildModelParameters: BuildModelParameters,
    private val taskGraph: BuildTreeWorkGraphController,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val projectLeaseRegistry: ProjectLeaseRegistry,
    private val stateTransitionControllerFactory: StateTransitionControllerFactory
) : BuildTreeLifecycleControllerFactory {
    override fun createRootBuildController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        return createController(targetBuild, workExecutor, finishExecutor)
    }

    override fun createController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        val workPreparer = createWorkPreparer(targetBuild)
        val modelCreator = createModelCreator(targetBuild)
        return DefaultBuildTreeLifecycleController(targetBuild, taskGraph, workPreparer, workExecutor, modelCreator, finishExecutor, stateTransitionControllerFactory)
    }

    fun createModelCreator(targetBuild: BuildLifecycleController) =
        DefaultBuildTreeModelCreator(buildModelParameters, targetBuild.gradle.owner, buildOperationExecutor, projectLeaseRegistry)

    fun createWorkPreparer(targetBuild: BuildLifecycleController) = DefaultBuildTreeWorkPreparer(targetBuild.gradle.owner, targetBuild)
}
