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

package org.gradle.internal.cc.impl.barrier

import org.gradle.StartParameter
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeFinishExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.cc.impl.VintageBuildTreeLifecycleControllerFactory
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier

/**
 * An extension of [VintageBuildTreeLifecycleControllerFactory] that adds configuration time barrier management to vintage mode.
 */
internal class BarrierAwareBuildTreeLifecycleControllerFactory(
    private val runner: VintageConfigurationTimeActionRunner,
    buildModelParameters: BuildModelParameters,
    taskGraph: BuildTreeWorkGraphController,
    buildOperationExecutor: BuildOperationExecutor,
    stateTransitionControllerFactory: StateTransitionControllerFactory,
    startParameter: StartParameter,
    parameterCarrierFactory: ToolingModelParameterCarrier.Factory,
    buildStateRegistry: BuildStateRegistry,
    buildOperationRunner: BuildOperationRunner
) : VintageBuildTreeLifecycleControllerFactory(
    buildModelParameters,
    taskGraph,
    buildOperationExecutor,
    stateTransitionControllerFactory,
    startParameter,
    parameterCarrierFactory,
    buildStateRegistry,
    buildOperationRunner
) {
    override fun createRootBuildController(
        targetBuild: BuildLifecycleController,
        workExecutor: BuildTreeWorkExecutor,
        finishExecutor: BuildTreeFinishExecutor
    ): BuildTreeLifecycleController {
        return createControllerImpl(
            BarrierAwareBuildTreeWorkPreparer(runner, createWorkPreparer(targetBuild)),
            BarrierAwareBuildTreeModelCreator(runner, createModelCreator(targetBuild)),
            targetBuild,
            workExecutor,
            finishExecutor
        )
    }
}
