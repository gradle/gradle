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
import org.gradle.initialization.exception.ExceptionAnalyser
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
    def exceptionAnalyzer = Mock(ExceptionAnalyser)
    def controller = new DefaultBuildTreeLifecycleController(buildController, taskGraph, workPreparer, workExecutor, modelCreator, finishExecutor, exceptionAnalyzer)
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
        1 * finishExecutor.finishBuildTree([]) >> ExecutionResult.succeeded()
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
        1 * exceptionAnalyzer.transform([failure]) >> reportableFailure
        1 * finishExecutor.finishBuildTree([failure]) >> ExecutionResult.succeeded()
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
        1 * exceptionAnalyzer.transform([failure]) >> reportableFailure
        1 * finishExecutor.finishBuildTree([failure]) >> ExecutionResult.succeeded()
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
        1 * finishExecutor.finishBuildTree([]) >> ExecutionResult.succeeded()
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
        1 * finishExecutor.finishBuildTree([failure]) >> ExecutionResult.succeeded()
        _ * exceptionAnalyzer.transform([failure]) >> reportableFailure
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
        1 * finishExecutor.finishBuildTree([]) >> ExecutionResult.succeeded()
    }

    def "collects configuration and build finish failures"() {
        def failure = new RuntimeException()
        def failure2 = new RuntimeException()

        when:
        controller.fromBuildModel(false, Stub(Function))

        then:
        def e = thrown(RuntimeException)
        e == reportableFailure

        and:
        1 * modelCreator.fromBuildModel(_) >> { throw failure }

        and:
        1 * finishExecutor.finishBuildTree([failure]) >> ExecutionResult.failed(failure2)
        _ * exceptionAnalyzer.transform([failure, failure2]) >> reportableFailure
    }
}
