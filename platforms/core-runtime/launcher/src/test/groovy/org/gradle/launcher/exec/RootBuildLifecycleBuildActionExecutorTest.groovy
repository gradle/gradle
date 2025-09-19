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

package org.gradle.launcher.exec

import org.gradle.StartParameter
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeLifecycleListener
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.work.ProjectParallelExecutionController
import org.gradle.problems.buildtree.ProblemStream
import spock.lang.Specification

class RootBuildLifecycleBuildActionExecutorTest extends Specification {

    def "fires events before and after build action is run"() {
        def listener = Mock(BuildTreeLifecycleListener)
        def buildAction = Stub(BuildAction)
        def buildActionRunner = Mock(BuildActionRunner)
        def buildStateRegistry = Mock(BuildStateRegistry)
        def projectParallelExecutionController = Mock(ProjectParallelExecutionController)
        def rootBuildState = Mock(RootBuildState)
        def buildTreeLifecycleController = Mock(BuildTreeLifecycleController)

        def executor = new RootBuildLifecycleBuildActionExecutor(
            Stub(BuildModelParameters),
            projectParallelExecutionController,
            listener,
            Stub(InternalProblems),
            Stub(BuildOperationProgressEventEmitter),
            Stub(StartParameter),
            Stub(ProblemStream),
            buildStateRegistry,
            buildActionRunner
        )

        when:
        executor.execute(buildAction)

        then:
        1 * projectParallelExecutionController.startProjectExecution(_)
        1 * listener.afterStart()
        1 * buildStateRegistry.createRootBuild(_) >> rootBuildState
        1 * rootBuildState.run(_) >> { args ->
            def action = args[0]
            action.apply(buildTreeLifecycleController)
        }
        1 * buildActionRunner.run(buildAction, buildTreeLifecycleController)
        1 * listener.beforeStop()
        1 * projectParallelExecutionController.finishProjectExecution()
        0 * listener._
    }

}
