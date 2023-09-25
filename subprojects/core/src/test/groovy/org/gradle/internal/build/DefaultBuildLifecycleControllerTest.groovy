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

package org.gradle.internal.build

import org.gradle.BuildListener
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.execution.BuildWorkExecutor
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Function

class DefaultBuildLifecycleControllerTest extends Specification {
    def buildListener = Mock(BuildListener)
    def buildModelLifecycleListener = Mock(BuildModelLifecycleListener)
    def workExecutor = Mock(BuildWorkExecutor)
    def workPreparer = Mock(BuildWorkPreparer)

    def settingsMock = Mock(SettingsInternal.class)
    def gradleMock = Mock(GradleInternal.class)

    def buildModelController = Mock(BuildModelController)
    def exceptionAnalyser = Mock(ExceptionAnalyser)
    def executionPlan = Mock(ExecutionPlan)
    def finalizedPlan = Mock(FinalizedExecutionPlan)
    def toolingControllerFactory = Mock(BuildToolingModelControllerFactory)
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def failure = new RuntimeException("main")
    def transformedException = new RuntimeException("transformed")

    def setup() {
        _ * exceptionAnalyser.transform(failure) >> transformedException
        def taskGraph = Stub(TaskExecutionGraphInternal)
        _ * gradleMock.taskGraph >> taskGraph
        def services = new DefaultServiceRegistry()
        services.add(Stub(BuildOutputCleanupRegistry))
        _ * gradleMock.services >> services
        _ * gradleMock.owner >> Stub(BuildState)
    }

    DefaultBuildLifecycleController controller() {
        return new DefaultBuildLifecycleController(gradleMock, buildModelController, exceptionAnalyser, buildListener,
            buildModelLifecycleListener, workPreparer, workExecutor, toolingControllerFactory, TestUtil.stateTransitionControllerFactory())
    }

    void testCanFinishBuildWhenNothingHasBeenDone() {
        def controller = controller()

        expect:
        expectBuildFinished("Configure")

        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testScheduleAndRunRequestedTasks() {
        expect:
        expectRequestedTasksScheduled()
        expectTasksRun()
        expectBuildFinished()

        def controller = controller()

        controller.prepareToScheduleTasks()
        def plan = controller.newWorkGraph()
        controller.populateWorkGraph(plan) { b -> b.addRequestedTasks(,) }
        controller.finalizeWorkGraph(plan)
        def executionResult = controller.executeTasks(plan)
        executionResult.failures.empty

        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testScheduleAndRunRequestedTasksMultipleTimes() {
        expect:
        expectRequestedTasksScheduled()
        expectTasksRun()
        expectTasksScheduled()
        expectTasksRun()
        expectBuildFinished()

        def controller = controller()

        controller.prepareToScheduleTasks()
        def plan1 = controller.newWorkGraph()
        controller.populateWorkGraph(plan1) { b -> b.addRequestedTasks(,) }
        controller.finalizeWorkGraph(plan1)
        def executionResult = controller.executeTasks(plan1)
        executionResult.failures.empty

        controller.prepareToScheduleTasks()
        def plan2 = controller.newWorkGraph()
        controller.populateWorkGraph(plan2) {}
        controller.finalizeWorkGraph(plan2)
        def executionResult2 = controller.executeTasks(plan2)
        executionResult2.failures.empty

        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testResetModelAfterSchedulingTasks() {
        expect:
        expectRequestedTasksScheduled()
        expectTasksRun()
        expectTasksScheduled()
        expectModelReset()
        expectBuildFinished()

        def controller = controller()

        controller.prepareToScheduleTasks()
        def plan1 = controller.newWorkGraph()
        controller.populateWorkGraph(plan1) { b -> b.addRequestedTasks(,) }
        controller.finalizeWorkGraph(plan1)

        def resetResult = controller.beforeModelReset()
        resetResult.failures.empty

        controller.resetModel()

        controller.prepareToScheduleTasks()
        def plan2 = controller.newWorkGraph()
        controller.populateWorkGraph(plan2) {}
        controller.finalizeWorkGraph(plan2)
        def executionResult = controller.executeTasks(plan2)
        executionResult.failures.empty

        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testLoadSettings() {
        expect:
        expectSettingsBuilt()
        expectBuildFinished("Configure")

        def controller = controller()
        controller.loadSettings()

        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testWithSettings() {
        def action = Mock(Function)

        when:
        expectSettingsBuilt()

        def controller = controller()
        def result = controller.withSettings(action)

        then:
        result == "result"

        and:
        1 * action.apply(settingsMock) >> "result"

        expect:
        expectBuildFinished("Configure")
        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testNotifiesListenerOnLoadSettingsFailure() {
        def failure = new RuntimeException()

        when:
        expectSettingsBuiltWithFailure(failure)

        def controller = this.controller()
        controller.loadSettings()

        then:
        def t = thrown RuntimeException
        t == failure

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * exceptionAnalyser.transform([failure]) >> transformedException
        1 * buildListener.buildFinished({ it.failure == transformedException })
        finishResult.failures.empty

        when:
        def discardResult = controller.beforeModelDiscarded(true)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, true)
        discardResult.failures.empty
    }

    void testLoadSettingsRethrowsPreviousFailure() {
        def failure = new RuntimeException()

        when:
        expectSettingsBuiltWithFailure(failure)

        def controller = this.controller()
        controller.loadSettings()

        then:
        def t = thrown RuntimeException
        t == failure

        when:
        controller.loadSettings()

        then:
        def t2 = thrown RuntimeException
        t2 == failure

        when:
        controller.configureProjects()

        then:
        def t3 = thrown RuntimeException
        t3 == failure
    }

    void testConfigureBuild() {
        def controller = controller()

        when:
        controller.configureProjects()

        then:
        1 * buildModelController.configuredModel >> gradleMock

        expect:
        expectBuildFinished("Configure")
        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testWithConfiguredBuild() {
        def action = Mock(Function)
        def controller = controller()

        when:
        controller.withProjectsConfigured(action)

        then:
        1 * buildModelController.configuredModel >> gradleMock
        1 * action.apply(gradleMock)

        expect:
        expectBuildFinished("Configure")
        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testGetConfiguredBuild() {
        when:
        1 * buildModelController.configuredModel >> gradleMock

        def controller = controller()
        def result = controller.getConfiguredBuild()

        then:
        result == gradleMock

        expect:
        expectBuildFinished("Configure")
        def finishResult = controller.finishBuild(null)
        finishResult.failures.empty

        def discardResult = controller.beforeModelDiscarded(false)
        discardResult.failures.empty
    }

    void testNotifiesListenerOnConfigureBuildFailure() {
        def failure = new RuntimeException()

        when:
        1 * buildModelController.configuredModel >> { throw failure }

        def controller = this.controller()
        controller.getConfiguredBuild()

        then:
        def t = thrown RuntimeException
        t == failure

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * exceptionAnalyser.transform([failure]) >> transformedException
        1 * buildListener.buildFinished({ it.failure == transformedException })
        finishResult.failures.empty

        when:
        def discardResult = controller.beforeModelDiscarded(true)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, true)
        discardResult.failures.empty
    }

    void testConfigureBuildRethrowsPreviousFailure() {
        def failure = new RuntimeException()

        when:
        1 * buildModelController.configuredModel >> { throw failure }

        def controller = this.controller()
        controller.configureProjects()

        then:
        def t = thrown RuntimeException
        t == failure

        when:
        controller.configureProjects()

        then:
        def t2 = thrown RuntimeException
        t2 == failure

        when:
        controller.loadSettings()

        then:
        def t3 = thrown RuntimeException
        t3 == failure
    }

    void testCanExecuteTasksWhenNothingHasBeenScheduled() {
        when:
        def controller = controller()
        def workGraph = controller.newWorkGraph()
        def result = controller.executeTasks(workGraph)

        then:
        result.failures.empty

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * buildListener.buildFinished({ it.failure == null })
        finishResult.failures.empty

        when:
        def discardResult = controller.beforeModelDiscarded(false)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, false)
        discardResult.failures.empty
    }

    void testNotifiesListenerOnTaskSchedulingFailure() {
        given:
        1 * workPreparer.newExecutionPlan() >> executionPlan
        1 * workPreparer.populateWorkGraph(gradleMock, executionPlan, _) >> { GradleInternal gradle, ExecutionPlan executionPlan, Consumer consumer -> consumer.accept(executionPlan) }
        1 * buildModelController.scheduleRequestedTasks(null, executionPlan,) >> { throw failure }

        when:
        def controller = this.controller()
        controller.prepareToScheduleTasks()
        def plan = controller.newWorkGraph()
        controller.populateWorkGraph(plan) { b -> b.addRequestedTasks(,) }

        then:
        def t = thrown RuntimeException
        t == failure

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * exceptionAnalyser.transform([failure]) >> transformedException
        1 * buildListener.buildFinished({ it.failure == transformedException && it.action == "Build" })
        finishResult.failures.empty

        when:
        def discardResult = controller.beforeModelDiscarded(true)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, true)
        discardResult.failures.empty
    }

    void testNotifiesListenerOnTaskExecutionFailure() {
        given:
        expectRequestedTasksScheduled()
        expectTasksRunWithFailure(failure)

        when:
        def controller = this.controller()
        controller.prepareToScheduleTasks()
        def plan = controller.newWorkGraph()
        controller.populateWorkGraph(plan) { b -> b.addRequestedTasks(,) }
        controller.finalizeWorkGraph(plan)
        def executionResult = controller.executeTasks(plan)

        then:
        executionResult.failures == [failure]

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * exceptionAnalyser.transform([failure]) >> transformedException
        1 * buildListener.buildFinished({ it.failure == transformedException })
        finishResult.failures.empty

        when:
        def discardResult = controller.beforeModelDiscarded(true)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, true)
        discardResult.failures.empty
    }

    void testNotifiesListenerOnBuildCompleteWithMultipleFailures() {
        def failure2 = new RuntimeException()

        given:
        expectRequestedTasksScheduled()
        expectTasksRunWithFailure(failure, failure2)

        when:
        def controller = this.controller()
        controller.prepareToScheduleTasks()
        def plan = controller.newWorkGraph()
        controller.populateWorkGraph(plan) { b -> b.addRequestedTasks(,) }
        controller.finalizeWorkGraph(plan)
        def executionResult = controller.executeTasks(plan)

        then:
        executionResult.failures == [failure, failure2]

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * exceptionAnalyser.transform([failure, failure2]) >> transformedException
        1 * buildListener.buildFinished({ it.failure == transformedException })
        finishResult.failures.empty

        when:
        def discardResult = controller.beforeModelDiscarded(true)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, true)
        discardResult.failures.empty
    }

    void testTransformsBuildFinishedListenerFailure() {
        given:
        expectRequestedTasksScheduled()
        expectTasksRun()

        and:
        def controller = controller()
        controller.prepareToScheduleTasks()
        def plan = controller.newWorkGraph()
        controller.populateWorkGraph(plan) { b -> b.addRequestedTasks(,) }
        controller.finalizeWorkGraph(plan)
        controller.executeTasks(plan)

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * buildListener.buildFinished({ it.failure == null }) >> { throw failure }
        finishResult.failures == [failure]

        when:
        def discardResult = controller.beforeModelDiscarded(true)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, true)
        discardResult.failures.empty
    }

    void testNotifiesListenersOnMultipleBuildFailuresAndBuildListenerFailure() {
        def failure2 = new RuntimeException()
        def failure3 = new RuntimeException()

        given:
        expectRequestedTasksScheduled()
        expectTasksRunWithFailure(failure, failure2)

        and:
        def controller = controller()
        controller.prepareToScheduleTasks()
        def plan = controller.newWorkGraph()
        controller.populateWorkGraph(plan) { b -> b.addRequestedTasks(,) }
        controller.finalizeWorkGraph(plan)

        when:
        def executionResult = controller.executeTasks(plan)

        then:
        executionResult.failures == [failure, failure2]

        when:
        def finishResult = controller.finishBuild(null)

        then:
        1 * exceptionAnalyser.transform([failure, failure2]) >> transformedException
        1 * buildListener.buildFinished({ it.failure == transformedException }) >> { throw failure3 }
        finishResult.failures == [failure3]

        when:
        def discardResult = controller.beforeModelDiscarded(true)

        then:
        1 * buildModelLifecycleListener.beforeModelDiscarded(gradleMock, true)
        discardResult.failures.empty
    }

    void testCannotGetModelAfterFinished() {
        given:
        def controller = controller()
        controller.finishBuild(null)
        controller.beforeModelDiscarded(false)

        when:
        controller.gradle

        then:
        thrown IllegalStateException
    }

    void testCannotLoadSettingsAfterFinished() {
        given:
        def controller = controller()
        controller.finishBuild(null)
        controller.beforeModelDiscarded(false)

        when:
        controller.loadSettings()

        then:
        thrown IllegalStateException
    }

    void testCannotConfigureBuildAfterFinished() {
        given:
        def controller = controller()
        controller.finishBuild(null)
        controller.beforeModelDiscarded(false)

        when:
        controller.configureProjects()

        then:
        thrown IllegalStateException
    }

    void testCannotRunMoreWorkAfterFinished() {
        given:
        def controller = controller()
        controller.finishBuild(null)
        controller.beforeModelDiscarded(false)

        when:
        controller.prepareToScheduleTasks()

        then:
        thrown IllegalStateException
    }

    private void expectSettingsBuilt() {
        1 * buildModelController.loadedSettings >> settingsMock
    }

    private void expectSettingsBuiltWithFailure(Throwable failure) {
        1 * buildModelController.loadedSettings >> { throw failure }
    }

    private void expectModelReset() {
        1 * gradleMock.resetState()
    }

    private void expectRequestedTasksScheduled() {
        1 * workPreparer.newExecutionPlan() >> executionPlan
        1 * buildModelController.prepareToScheduleTasks()
        1 * workPreparer.populateWorkGraph(gradleMock, executionPlan, _) >> { GradleInternal gradle, ExecutionPlan executionPlan, Consumer consumer -> consumer.accept(executionPlan) }
        1 * buildModelController.scheduleRequestedTasks(null, executionPlan,)
        1 * workPreparer.finalizeWorkGraph(gradleMock, executionPlan) >> finalizedPlan
    }

    private void expectTasksScheduled() {
        1 * workPreparer.newExecutionPlan() >> executionPlan
        1 * buildModelController.prepareToScheduleTasks()
        1 * workPreparer.populateWorkGraph(gradleMock, executionPlan, _) >> { GradleInternal gradle, ExecutionPlan executionPlan, Consumer consumer -> consumer.accept(executionPlan) }
        1 * workPreparer.finalizeWorkGraph(gradleMock, executionPlan) >> finalizedPlan
    }

    private void expectTasksRun() {
        1 * workExecutor.execute(gradleMock, finalizedPlan) >> ExecutionResult.succeeded()
    }

    private void expectTasksRunWithFailure(Throwable failure, Throwable other = null) {
        def failures = other == null ? [failure] : [failure, other]
        1 * workExecutor.execute(gradleMock, finalizedPlan) >> ExecutionResult.maybeFailed(failures)
    }

    private void expectBuildFinished(String action = "Build") {
        1 * buildListener.buildFinished({ it.failure == null && it.action == action })
        1 * buildModelLifecycleListener.beforeModelDiscarded(_, false)
    }
}
