/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.api.BuildCancelledException
import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDestroyablesInternal
import org.gradle.api.internal.tasks.TaskLocalStateInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskDestroyables
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.resources.ResourceLockState
import org.gradle.internal.scheduler.AbstractSchedulingTest
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.TestUtil.createRootProject
import static org.gradle.util.WrapUtil.toList

@CleanupTestDirectory
@UsesNativeServices
class DefaultTaskExecutionPlanTest extends AbstractSchedulingTest {

    // Naming the field "temporaryFolder" since that is the default field intercepted by the
    // @CleanupTestDirectory annotation.
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    DefaultTaskExecutionPlan executionPlan
    ProjectInternal root
    def workerLeaseService = Mock(WorkerLeaseService)
    def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
    def gradle = Mock(GradleInternal)

    def setup() {
        root = createRootProject(temporaryFolder.testDirectory)
        executionPlan = new DefaultTaskExecutionPlan(workerLeaseService, Mock(GradleInternal))
        _ * workerLeaseService.getProjectLock(_, _) >> Mock(ResourceLock) {
            _ * isLocked() >> false
            _ * tryLock() >> true
        }
        _ * workerLease.tryLock() >> true
    }

    def "schedules task dependencies in name order when there are no dependencies between them"() {
        given:
        def a = task("a")
        def b = task("b")
        def c = task("c")
        def d = task("d", dependsOn: [b, a, c])

        when:
        addToGraphAndPopulate([d])

        then:
        executes(a, b, c, d)
    }

    def "schedules a single batch of tasks in name order"() {
        given:
        def a = task("a")
        def b = task("b")
        def c = task("c")

        when:
        addToGraphAndPopulate(toList(b, c, a))

        then:
        executes(a, b, c)
    }

    def "schedules separately added tasks in order added"() {
        given:
        def a = task("a")
        def b = task("b")
        def c = task("c")
        def d = task("d")

        when:
        addToGraph(toList(c, b))
        addToGraph(toList(d, a))
        determineExecutionPlan()

        then:
        executes(b, c, a, d)
    }

    @Unroll
    def "schedules #orderingRule task dependencies in name order"() {
        given:
        def a = task("a")
        def b = task("b")
        def c = task("c", (orderingRule): [b, a])
        def d = task("d", dependsOn: [b, a])

        when:
        addToGraphAndPopulate([c, d])

        then:
        executes(a, b, c, d)

        where:
        orderingRule << ['mustRunAfter', 'shouldRunAfter']
    }

    def "mustRunAfter dependencies are scheduled before regular dependencies"() {
        def a = task("a")
        def b = task("b")
        def c = task("c", dependsOn: [a], mustRunAfter: [b])
        def d = task("d", dependsOn: [b])

        when:
        addToGraphAndPopulate([c, d])

        then:
        executes(b, a, c, d)
    }

    def "shouldRunAfter dependencies are scheduled before mustRunAfter dependencies"() {
        def a = task("a")
        def b = task("b")
        def c = task("c", mustRunAfter: [a], shouldRunAfter: [b])
        def d = task("d", dependsOn: [a, b])

        when:
        addToGraphAndPopulate([c, d])

        then:
        executes(b, a, c, d)
    }

    def "stops returning tasks when build is cancelled"() {
        def a = task("a")
        def b = task("b")

        when:
        addToGraphAndPopulate([a, b])
        executionPlan.cancelExecution()

        then:
        executedTasks == []
        failures*.class == [BuildCancelledException]
        failures*.message == ['Build cancelled.']
    }

    def "clear removes all tasks"() {
        given:
        def a = task("a")

        when:
        addToGraphAndPopulate(toList(a))
        executionPlan.clear()

        then:
        allTasks == ([] as Set)
        executedTasks == []
    }

    def "can add additional tasks after execution and clear"() {
        given:
        def a = task("a")
        def b = task("b")

        when:
        addToGraphAndPopulate([a])

        then:
        executes(a)

        when:
        executionPlan.clear()
        addToGraphAndPopulate([b])

        then:
        executes(b)
    }

    @Override
    void addToGraph(List tasks) {
        executionPlan.addToTaskGraph(tasks)
    }

    @Override
    void determineExecutionPlan() {
        executionPlan.determineExecutionPlan()
    }

    @Override
    void useFilter(Spec filter) {
        executionPlan.useFilter(filter)
    }

    @Override
    protected List<? extends Throwable> getFailures() {
        def failures = []
        executionPlan.collectFailures(failures)
        return failures
    }

    @Override
    protected void continueOnFailure() {
        executionPlan.continueOnFailure = true
    }

    @Override
    protected void executes(Object... expectedTasks) {
        assert executionPlan.tasks as List == expectedTasks as List
        assert expectedTasks == expectedTasks as List
    }

    @Override
    protected void filtered(Object... expectedTasks) {
        assert executionPlan.filteredTasks == expectedTasks as Set
    }

    @Override
    protected Set getAllTasks() {
        return executionPlan.tasks as Set
    }

    List getExecutedTasks() {
        def tasks = []
        def moreTasks = true
        while (moreTasks) {
            def nextNode = executionPlan.selectNextTask(workerLease, Mock(ResourceLockState))
            if (nextNode != null) {
                if (!nextNode.isComplete()) {
                    tasks << nextNode.task
                    executionPlan.taskComplete(nextNode)
                }
            }
            moreTasks = executionPlan.hasWorkRemaining()
        }
        return tasks
    }

    private TaskDependency taskDependencyResolvingTo(TaskInternal task, List<Task> tasks) {
        Mock(TaskDependency) {
            getDependencies(task) >> tasks
        }
    }

    private TaskDependency brokenDependencies() {
        Mock(TaskDependency) {
            0 * getDependencies(_)
        }
    }

    private void dependsOn(TaskInternal task, List<Task> dependsOnTasks) {
        task.getTaskDependencies() >> taskDependencyResolvingTo(task, dependsOnTasks)
    }

    private void mustRunAfter(TaskInternal task, List<Task> mustRunAfterTasks) {
        task.getMustRunAfter() >> taskDependencyResolvingTo(task, mustRunAfterTasks)
    }

    private void finalizedBy(TaskInternal task, List<Task> finalizedByTasks) {
        task.getFinalizedBy() >> taskDependencyResolvingTo(task, finalizedByTasks)
    }

    private void shouldRunAfter(TaskInternal task, List<Task> shouldRunAfterTasks) {
        task.getShouldRunAfter() >> taskDependencyResolvingTo(task, shouldRunAfterTasks)
    }

    private void failure(TaskInternal task, final RuntimeException failure) {
        task.state.getFailure() >> failure
        task.state.rethrowFailure() >> { throw failure }
    }

    private TaskOutputsInternal emptyTaskOutputs() {
        Stub(TaskOutputsInternal)
    }

    private TaskDestroyables emptyTaskDestroys() {
        Stub(TaskDestroyablesInternal)
    }

    private TaskLocalStateInternal emptyTaskLocalState() {
        Stub(TaskLocalStateInternal)
    }

    private TaskInputsInternal emptyTaskInputs() {
        Stub(TaskInputsInternal)
    }

    @Override
    protected def task(Map options, final String name) {
        def task = createTask(name)
        relationships(options, task)
        if (options.failure) {
            failure(task, options.failure)
        }
        task.getDidWork() >> (options.containsKey('didWork') ? options.didWork : true)
        task.getOutputs() >> emptyTaskOutputs()
        task.getDestroyables() >> emptyTaskDestroys()
        task.getLocalState() >> emptyTaskLocalState()
        task.getInputs() >> emptyTaskInputs()
        return task
    }

    @Override
    protected void relationships(Map options, def task) {
        dependsOn(task, options.dependsOn ?: [])
        mustRunAfter(task, options.mustRunAfter ?: [])
        shouldRunAfter(task, options.shouldRunAfter ?: [])
        finalizedBy(task, options.finalizedBy ?: [])
    }

    @Override
    protected def filteredTask(final String name) {
        def task = createTask(name)
        task.getTaskDependencies() >> brokenDependencies()
        task.getMustRunAfter() >> brokenDependencies()
        task.getShouldRunAfter() >> brokenDependencies()
        task.getFinalizedBy() >> taskDependencyResolvingTo(task, [])
        return task
    }

    @Override
    protected TaskInternal createTask(final String name) {
        TaskInternal task = Mock()
        TaskStateInternal state = Mock()
        task.getProject() >> root
        task.name >> name
        task.path >> ':' + name
        task.identityPath >> Path.path(':' + name)
        task.state >> state
        task.toString() >> "task $name"
        task.compareTo(_ as TaskInternal) >> { TaskInternal taskInternal ->
            return name.compareTo(taskInternal.getName())
        }
        task.getOutputs() >> emptyTaskOutputs()
        task.getDestroyables() >> emptyTaskDestroys()
        task.getLocalState() >> emptyTaskLocalState()
        task.getInputs() >> emptyTaskInputs()
        return task
    }
}

