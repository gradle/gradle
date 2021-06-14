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

import org.gradle.composite.internal.IncludedBuildControllers
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeModelCreator
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.BuildTreeWorkPreparer
import org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController
import org.gradle.internal.buildtree.DefaultBuildTreeModelCreator
import org.gradle.internal.buildtree.DefaultBuildTreeWorkPreparer


class DefaultBuildTreeLifecycleControllerFactory(
    private val controllers: IncludedBuildControllers,
    private val exceptionAnalyser: ExceptionAnalyser
) : BuildTreeLifecycleControllerFactory {
    override fun createController(targetBuild: BuildLifecycleController, workExecutor: BuildTreeWorkExecutor, finishExecutor: BuildTreeFinishExecutor): BuildTreeLifecycleController {
        val workPreparer: BuildTreeWorkPreparer = DefaultBuildTreeWorkPreparer(targetBuild, controllers)
        val modelCreator: BuildTreeModelCreator = DefaultBuildTreeModelCreator(targetBuild)
        return DefaultBuildTreeLifecycleController(targetBuild, workPreparer, workExecutor, modelCreator, finishExecutor, exceptionAnalyser)
    }
}
