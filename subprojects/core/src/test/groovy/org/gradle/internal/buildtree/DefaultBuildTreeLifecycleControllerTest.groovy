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
import org.gradle.composite.internal.IncludedBuildTaskGraph
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.ExecutionResult
import spock.lang.Specification

import java.util.function.Function
import java.util.function.Supplier

class DefaultBuildTreeLifecycleControllerTest extends Specification {
    def gradle = Mock(GradleInternal)
    def buildController = Mock(BuildLifecycleController)
    def taskGraph = Mock(IncludedBuildTaskGraph)
    def workPreparer = Mock(BuildTreeWorkPreparer)
    def workExecutor = Mock(BuildTreeWorkExecutor)
    def modelCreator = Mock(BuildTreeModelCreator)
    def finishExecutor = Mock(BuildTreeFinishExecutor)
    def controller = new DefaultBuildTreeLifecycleController(buildController, taskGraph, workPreparer, workExecutor, modelCreator, finishExecutor)
    def reportableFailure = new RuntimeException()

    def setup() {
        buildController.gradle >> gradle
    }

    def "runs tasks"() {
        when:
        controller.scheduleAndRunTasks()

        then:
        1 * taskGraph.withNewTaskGraph(_) >> { Supplier supplier -> supplier.get() }

        and:
        1 * workPreparer.scheduleRequestedTasks()
        1 * workExecutor.execute() >> ExecutionResult.succeeded()

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
        1 * taskGraph.withNewTaskGraph(_) >> { Supplier supplier -> supplier.get() }

        and:
        1 * workPreparer.scheduleRequestedTasks() >> { throw failure }

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
        1 * taskGraph.withNewTaskGraph(_) >> { Supplier supplier -> supplier.get() }

        and:
        1 * workPreparer.scheduleRequestedTasks()
        1 * workExecutor.execute() >> ExecutionResult.failed(failure)

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
        1 * taskGraph.withNewTaskGraph(_) >> { Supplier supplier -> supplier.get() }

        and:
        1 * workPreparer.scheduleRequestedTasks()
        1 * workExecutor.execute() >> ExecutionResult.succeeded()

        and:
        1 * finishExecutor.finishBuildTree([]) >> reportableFailure
    }

    def "runs action after running tasks when task execution is requested"() {
        def action = Mock(Function)

        when:
        def result = controller.fromBuildModel(true, action)

        then:
        result == "result"

        and:
        1 * taskGraph.withNewTaskGraph(_) >> { Supplier supplier -> supplier.get() }
        1 * workPreparer.scheduleRequestedTasks()
        1 * workExecutor.execute() >> ExecutionResult.succeeded()

        and:
        1 * modelCreator.fromBuildModel(action) >> "result"

        and:
        1 * finishExecutor.finishBuildTree([]) >> null
    }

    def "does not run action if task execution fails"() {
        def action = Mock(Function)
        def failure = new RuntimeException()

        when:
        controller.fromBuildModel(true, action)

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * taskGraph.withNewTaskGraph(_) >> { Supplier supplier -> supplier.get() }
        1 * workPreparer.scheduleRequestedTasks()
        1 * workExecutor.execute() >> ExecutionResult.failed(failure)
        0 * action._

        and:
        1 * finishExecutor.finishBuildTree([failure]) >> reportableFailure
    }

    def "runs action when tasks are not requested"() {
        def action = Mock(Function)

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
        controller.fromBuildModel(false, Stub(Function))

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * modelCreator.fromBuildModel(_) >> { throw failure }

        and:
        1 * finishExecutor.finishBuildTree([failure]) >> reportableFailure
    }
}
