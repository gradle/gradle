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

package org.gradle.internal.buildtree

import org.gradle.api.internal.GradleInternal
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildToolingModelAction
import org.gradle.internal.build.ExecutionResult
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Function

class DefaultBuildTreeLifecycleControllerTest extends Specification {
    def gradle = Mock(GradleInternal)
    def buildController = Mock(BuildLifecycleController)
    def taskGraph = Mock(BuildTreeWorkGraphController)
    def workGraph = Mock(BuildTreeWorkGraph)
    def workPreparer = Mock(BuildTreeWorkPreparer)
    def workExecutor = Mock(BuildTreeWorkExecutor)
    def modelCreator = Mock(BuildTreeModelCreator)
    def finishExecutor = Mock(BuildTreeFinishExecutor)
    def controller = new DefaultBuildTreeLifecycleController(buildController, taskGraph, workPreparer, workExecutor, modelCreator, finishExecutor, TestUtil.stateTransitionControllerFactory())
    def reportableFailure = new RuntimeException()

    def setup() {
        buildController.gradle >> gradle
    }

    def "runs tasks"() {
        when:
        controller.scheduleAndRunTasks()

        then:
        1 * taskGraph.withNewWorkGraph(_) >> { Function supplier -> supplier.apply(workGraph) }

        and:
        1 * workPreparer.scheduleRequestedTasks(workGraph)
        1 * workExecutor.execute(workGraph) >> ExecutionResult.succeeded()

        and:
        1 * finishExecutor.finishBuildTree([]) >> null
    }

    def "runs tasks and collects failure to schedule tasks"() {
        def failure = new RuntimeException()

        when:
        controller.scheduleAndRunTasks()

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * taskGraph.withNewWorkGraph(_) >> { Function supplier -> supplier.apply(workGraph) }

        and:
        1 * workPreparer.scheduleRequestedTasks(workGraph) >> { throw failure }

        and:
        1 * finishExecutor.finishBuildTree([failure]) >> reportableFailure
    }

    def "runs tasks and collects failure to execute tasks"() {
        def failure = new RuntimeException()

        when:
        controller.scheduleAndRunTasks()

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * taskGraph.withNewWorkGraph(_) >> { Function supplier -> supplier.apply(workGraph) }

        and:
        1 * workPreparer.scheduleRequestedTasks(workGraph)
        1 * workExecutor.execute(workGraph) >> ExecutionResult.failed(failure)

        and:
        1 * finishExecutor.finishBuildTree([failure]) >> reportableFailure
    }

    def "runs tasks and throws failure to finish build"() {
        when:
        controller.scheduleAndRunTasks()

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * taskGraph.withNewWorkGraph(_) >> { Function supplier -> supplier.apply(workGraph) }

        and:
        1 * workPreparer.scheduleRequestedTasks(workGraph)
        1 * workExecutor.execute(workGraph) >> ExecutionResult.succeeded()

        and:
        1 * finishExecutor.finishBuildTree([]) >> reportableFailure
    }

    def "runs action after running tasks when task execution is requested"() {
        def action = Mock(BuildToolingModelAction)

        when:
        def result = controller.fromBuildModel(true, action)

        then:
        result == "result"

        and:
        1 * taskGraph.withNewWorkGraph(_) >> { Function supplier -> supplier.apply(workGraph) }
        1 * workPreparer.scheduleRequestedTasks(workGraph)
        1 * workExecutor.execute(workGraph) >> ExecutionResult.succeeded()

        and:
        1 * modelCreator.fromBuildModel(action) >> "result"

        and:
        1 * finishExecutor.finishBuildTree([]) >> null
    }

    def "does not run action if task execution fails"() {
        def action = Mock(BuildToolingModelAction)
        def failure = new RuntimeException()

        when:
        controller.fromBuildModel(true, action)

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * taskGraph.withNewWorkGraph(_) >> { Function supplier -> supplier.apply(workGraph) }
        1 * workPreparer.scheduleRequestedTasks(workGraph)
        1 * workExecutor.execute(workGraph) >> ExecutionResult.failed(failure)
        0 * action._

        and:
        1 * finishExecutor.finishBuildTree([failure]) >> reportableFailure
    }

    def "runs action when tasks are not requested"() {
        def action = Mock(BuildToolingModelAction)

        when:
        def result = controller.fromBuildModel(false, action)

        then:
        result == "result"

        and:
        1 * modelCreator.fromBuildModel(action) >> "result"

        and:
        1 * finishExecutor.finishBuildTree([]) >> null
    }

    def "collects failure to create model"() {
        def failure = new RuntimeException()

        when:
        controller.fromBuildModel(false, Stub(BuildToolingModelAction))

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * modelCreator.fromBuildModel(_) >> { throw failure }

        and:
        1 * finishExecutor.finishBuildTree([failure]) >> reportableFailure
    }

    def "can run action against model prior to invoking build"() {
        def action = Mock(Consumer)

        when:
        controller.beforeBuild(action)

        then:
        1 * action.accept(gradle)
        0 * action._
    }

    def "cannot run action against model once build has started"() {
        def action = Mock(Consumer)
        def modelAction = Mock(BuildToolingModelAction)

        given:
        _ * modelCreator.fromBuildModel(modelAction) >> {
            controller.beforeBuild(action)
        }

        when:
        controller.fromBuildModel(false, modelAction)

        then:
        1 * finishExecutor.finishBuildTree(_) >> { args ->
            throw args[0][0]
        }

        thrown(IllegalStateException)

        when:
        controller.beforeBuild(action)

        then:
        thrown(IllegalStateException)
    }
}
