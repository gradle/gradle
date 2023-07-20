/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.execution.plan

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.file.Stat
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.Path
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import javax.annotation.Nullable
import java.util.function.Consumer

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class DefaultExecutionPlanParallelTest extends AbstractExecutionPlanSpec {
    DefaultExecutionPlan executionPlan
    DefaultFinalizedExecutionPlan finalizedPlan

    def accessHierarchies = new ExecutionNodeAccessHierarchies(CASE_SENSITIVE, Stub(Stat))
    def taskNodeFactory = new TaskNodeFactory(project.gradle, Stub(DocumentationRegistry), Stub(BuildTreeWorkGraphController), nodeValidator, new TestBuildOperationExecutor(), accessHierarchies)

    def setup() {
        def dependencyResolver = new TaskDependencyResolver([new TaskNodeDependencyResolver(taskNodeFactory)])
        executionPlan = new DefaultExecutionPlan(Path.ROOT.toString(), taskNodeFactory, new OrdinalGroupFactory(), dependencyResolver, accessHierarchies.outputHierarchy, accessHierarchies.destroyableHierarchy, coordinator)
    }

    Node priorityNode(Map<String, ?> options = [:]) {
        return new TestPriorityNode(options.failure)
    }

    TaskInternal task(
        Map<String, ?> options = [:], String name) {
        def task = createTask(name, options.project ?: this.project, options.type ?: TaskInternal)
        _ * task.taskDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.lifecycleDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.finalizedBy >> taskDependencyResolvingTo(task, options.finalizedBy ?: [])
        _ * task.shouldRunAfter >> taskDependencyResolvingTo(task, options.shouldRunAfter ?: [])
        _ * task.mustRunAfter >> taskDependencyResolvingTo(task, options.mustRunAfter ?: [])
        _ * task.sharedResources >> (options.resources ?: [])
        _ * task.taskIdentity >> TestTaskIdentities.create(name, DefaultTask, project as ProjectInternal)
        TaskStateInternal state = Mock()
        _ * task.state >> state
        if (options.failure != null) {
            failure(task, options.failure)
        }
        return task
    }

    Node node(Map<String, ?> options = [:], String name) {
        def dependencies = nodes(options.dependsOn)
        def preExecute = nodes(options.preNodes)
        def postExecute = nodes(options.postNodes)
        def node = new TestNode(name, dependencies, preExecute, postExecute, options.failure)
        return node
    }

    List<Node> nodes(Object value) {
        if (value == null) {
            return []
        } else if (value instanceof Node) {
            return [value]
        } else {
            return value
        }
    }

    def "runs finalizer and its dependencies after finalized task"() {
        given:
        Task dep = task("dep", type: Async)
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizerDep2 = task("finalizerDep2", type: Async)
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep1, finalizerDep2])
        Task finalized = task("finalized", type: Async, dependsOn: [dep], finalizedBy: [finalizer])
        Task task = task("task", type: Async, dependsOn: [finalized])

        when:
        addToGraphAndPopulate(task)

        then:
        executionPlan.tasks as List == [dep, finalized, finalizerDep1, finalizerDep2, finalizer, task]
        ordinalGroups == [0, 0, 0, 0, 0, 0]
        reachableFromEntryPoint == [true, true, false, false, false, true]
        assertTaskReady(dep)
        assertTaskReady(finalized)
        assertTasksReady(finalizerDep1, finalizerDep2, task)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    def "does not attempt to run finalizer of task whose dependencies have failed"() {
        given:
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task finalizerDepDep = task("finalizerDepDep", type: Async)
        Task finalizerDep = task("finalizerDep", type: Async, dependsOn: [finalizerDepDep])
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task finalized = task("finalized", type: Async, dependsOn: [broken], finalizedBy: [finalizer])
        Task task = task("task", type: Async, dependsOn: [finalized])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(task)

        then:
        executionPlan.tasks as List == [broken, finalized, finalizerDepDep, finalizerDep, finalizer, task]
        assertTaskReady(broken)
        assertAllWorkComplete(continueOnFailure)

        where:
        continueOnFailure << [false, true]
    }

    def "finalizer tasks are executed on task failure but dependents of failed task are not"() {
        Task finalizerDepDep = task("finalizerDepDep")
        Task finalizerDep = task("finalizerDep", dependsOn: [finalizerDepDep])
        Task finalizer = task("finalizer", dependsOn: [finalizerDep])
        Task broken = task("broken", finalizedBy: [finalizer], failure: new RuntimeException())
        Task task = task("task", dependsOn: [broken])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(task)

        then:
        executionPlan.tasks as List == [broken, finalizerDepDep, finalizerDep, finalizer, task]
        assertTaskReady(broken)
        assertTaskReady(finalizerDepDep, true)
        assertTaskReady(finalizerDep)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()

        where:
        continueOnFailure << [false, true]
    }

    def "does not run finalizer when its dependency fails"() {
        given:
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task finalizerDep = task("finalizerDep", type: Async, dependsOn: [broken])
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task finalized = task("finalized", type: Async, finalizedBy: [finalizer])
        Task task = task("task", type: Async, dependsOn: [finalized])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(task)

        then:
        executionPlan.tasks as List == [finalized, broken, finalizerDep, finalizer, task]
        assertTaskReady(finalized)
        assertTasksReady(broken, task)
        assertAllWorkComplete(true)

        where:
        continueOnFailure << [false, true]
    }

    def "task and finalizer are not executed when unrelated finalized task fails"() {
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task broken = task("broken", type: Async, finalizedBy: [finalizer1], failure: new RuntimeException())
        Task finalizerDep2 = task("finalizerDep2", type: Async)
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task unrelated = task("unrelated", type: Async, finalizedBy: [finalizer2])

        when:
        addToGraphAndPopulate(broken, unrelated)

        then:
        executionPlan.tasks as List == [broken, finalizerDep1, finalizer1, unrelated, finalizerDep2, finalizer2]
        assertNextTaskReady(broken)
        assertTaskReady(finalizerDep1, true)
        assertTaskReadyAndNoMoreToStart(finalizer1)
        assertAllWorkComplete()
    }

    def "task and finalizer are executed when unrelated finalized task fails and continue on failure"() {
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task broken = task("broken", type: Async, finalizedBy: [finalizer1], failure: new RuntimeException())
        Task finalizerDep2 = task("finalizerDep2", type: Async)
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task unrelated = task("unrelated", type: Async, finalizedBy: [finalizer2])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate(broken, unrelated)

        then:
        executionPlan.tasks as List == [broken, finalizerDep1, finalizer1, unrelated, finalizerDep2, finalizer2]
        assertNextTaskReady(broken)
        assertTasksReady(finalizerDep1, unrelated)
        assertTasksReady(finalizer1, finalizerDep2)
        assertTaskReadyAndNoMoreToStart(finalizer2)
        assertAllWorkComplete()
    }

    def "finalizer and its dependencies run after the last task to be finalized"() {
        given:
        Task finalizerDep = task("finalizerDep", type: Async)
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task dep = task("dep", type: Async)
        Task a = task("a", type: Async, finalizedBy: [finalizer], dependsOn: [dep])
        Task b = task("b", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [dep, a, b, finalizerDep, finalizer]
        assertTasksReady(dep, b)
        assertTaskReady(a)
        assertTaskReady(finalizerDep)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    def "finalizer of multiple tasks and its dependencies run after the last task to be finalized when some do not start"() {
        given:
        Task finalizerDep = task("finalizerDep", type: Async)
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task a = task("a", type: Async, finalizedBy: [finalizer], dependsOn: [broken])
        Task b = task("b", type: Async, finalizedBy: [finalizer])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [broken, a, b, finalizerDep, finalizer]
        assertNextTaskReady(broken)
        assertTaskReady(b)
        assertTaskReady(finalizerDep)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    def "finalizer of multiple tasks and its dependencies do not run when none of the finalized tasks start"() {
        given:
        Task finalizerDep = task("finalizerDep", type: Async)
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task a = task("a", type: Async, finalizedBy: [finalizer], dependsOn: [broken])
        Task b = task("b", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [broken, a, b, finalizerDep, finalizer]
        assertNextTaskReady(broken)
        assertAllWorkComplete()
    }

    def "dependency of multiple finalizers runs after the first task to be finalized"() {
        given:
        Task finalizerDepDep = task("finalizerDepDep", type: Async)
        Task finalizerDep1 = task("finalizerDep1", type: Async, dependsOn: [finalizerDepDep])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [finalizerDepDep])
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task a = task("a", type: Async, finalizedBy: [finalizer1])
        Task b = task("b", type: Async, finalizedBy: [finalizer2], dependsOn: [a])

        when:
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [a, finalizerDepDep, finalizerDep1, finalizer1, b, finalizerDep2, finalizer2]
        ordinalGroups == [0, null, 0, 0, 1, 1, 1]
        reachableFromEntryPoint == [true, false, false, false, true, false, false]
        assertTaskReady(a)
        assertTasksReady(finalizerDepDep, b)
        assertTasksReady(finalizerDep1, finalizerDep2)
        assertTasksReadyAndNoMoreToStart(finalizer1, finalizer2)
        assertAllWorkComplete()
    }

    def "dependency of multiple finalizers runs after the first task to be finalized when one finalizer does not run"() {
        given:
        Task finalizerDepDep = task("finalizerDepDep", type: Async)
        Task finalizerDep1 = task("finalizerDep1", type: Async, dependsOn: [finalizerDepDep])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [finalizerDepDep])
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task a = task("a", type: Async, finalizedBy: [finalizer1], dependsOn: [broken])
        Task b = task("b", type: Async, finalizedBy: [finalizer2])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [broken, a, finalizerDepDep, finalizerDep1, finalizer1, b, finalizerDep2, finalizer2]
        assertNextTaskReady(broken)
        assertTaskReady(b)
        assertTaskReady(finalizerDepDep)
        assertTaskReady(finalizerDep2)
        assertTaskReadyAndNoMoreToStart(finalizer2)
        assertAllWorkComplete()
    }

    def "dependency of multiple finalizers does not run when none of the finalizers run"() {
        given:
        Task finalizerDepDep = task("finalizerDepDep", type: Async)
        Task finalizerDep1 = task("finalizerDep1", type: Async, dependsOn: [finalizerDepDep])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [finalizerDepDep])
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task a = task("a", type: Async, finalizedBy: [finalizer1], dependsOn: [broken])
        Task b = task("b", type: Async, finalizedBy: [finalizer2], dependsOn: [a])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [broken, a, finalizerDepDep, finalizerDep1, finalizer1, b, finalizerDep2, finalizer2]
        assertTaskReady(broken)
        assertAllWorkComplete(continueOnFailure)

        where:
        continueOnFailure << [true, false]
    }

    def "finalizers do not run when shared dependency does not run"() {
        given:
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task finalizerDep1 = task("finalizerDep1", type: Async, dependsOn: [broken])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [broken])
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task a = task("a", type: Async, finalizedBy: [finalizer1])
        Task b = task("b", type: Async, finalizedBy: [finalizer2])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [a, broken, finalizerDep1, finalizer1, b, finalizerDep2, finalizer2]
        assertTasksReady(a, b)
        assertTaskReady(broken)
        assertAllWorkComplete(true)

        where:
        continueOnFailure << [true, false]
    }

    def "finalizer that is dependency of another finalizer runs when the task it finalizes is complete"() {
        given:
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [finalizer1])
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task a = task("a", type: Async, finalizedBy: [finalizer1])
        Task b = task("b", type: Async, finalizedBy: [finalizer2], dependsOn: [a])

        when:
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [a, finalizerDep1, finalizer1, b, finalizerDep2, finalizer2]
        ordinalGroups == [0, 0, 0, 1, 1, 1]
        reachableFromEntryPoint == [true, false, false, true, false, false]
        assertTaskReady(a)
        assertTasksReady(finalizerDep1, b)
        assertTaskReady(finalizer1)
        assertTaskReady(finalizerDep2)
        assertTaskReadyAndNoMoreToStart(finalizer2)
        assertAllWorkComplete()
    }

    def "finalizer that is dependency of another finalizer runs when the task it finalizes does not run but other finalized task does"() {
        given:
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [finalizer1])
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task a = task("a", type: Async, finalizedBy: [finalizer1], dependsOn: [broken])
        Task b = task("b", type: Async, finalizedBy: [finalizer2])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [broken, a, finalizerDep1, finalizer1, b, finalizerDep2, finalizer2]
        assertNextTaskReady(broken)
        assertTaskReady(b)
        assertTaskReady(finalizerDep1)
        assertTaskReady(finalizer1)
        assertTaskReady(finalizerDep2)
        assertTaskReadyAndNoMoreToStart(finalizer2)
        assertAllWorkComplete()
    }

    def "finalizer that is dependency of another finalizer does not run when finalized tasks do not run"() {
        given:
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [finalizer1])
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2])
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task a = task("a", type: Async, finalizedBy: [finalizer1], dependsOn: [broken])
        Task b = task("b", type: Async, finalizedBy: [finalizer2], dependsOn: [a])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [broken, a, finalizerDep1, finalizer1, b, finalizerDep2, finalizer2]
        assertTaskReady(broken)
        assertAllWorkComplete(continueOnFailure)

        where:
        continueOnFailure << [true, false]
    }

    def "finalizer of multiple tasks and its dependencies run after last task to be finalized when finalized tasks have dependency relationships"() {
        given:
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizerDep2 = task("finalizerDep2", type: Async)
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2], finalizedBy: [finalizer1])
        Task depDep = task("depDep", type: Async, finalizedBy: [finalizer1])
        Task dep = task("dep", type: Async, dependsOn: [depDep])
        Task task = task("task", type: Async, finalizedBy: [finalizer2], dependsOn: [dep])

        when:
        addToGraphAndPopulate(task)

        then:
        executionPlan.tasks as List == [depDep, dep, task, finalizerDep2, finalizer2, finalizerDep1, finalizer1]
        assertTaskReady(depDep)
        assertTaskReady(dep)
        assertTaskReady(task)
        assertTaskReady(finalizerDep2)
        assertTaskReady(finalizer2)
        assertTaskReady(finalizerDep1)
        assertTaskReadyAndNoMoreToStart(finalizer1)
        assertAllWorkComplete()
    }

    def "finalizer of multiple tasks and its dependencies do not run when finalized tasks have dependency relationships but do not run"() {
        given:
        Task finalizerDep1 = task("finalizerDep1", type: Async)
        Task finalizer1 = task("finalizer1", type: Async, dependsOn: [finalizerDep1])
        Task finalizerDep2 = task("finalizerDep2", type: Async)
        Task finalizer2 = task("finalizer2", type: Async, dependsOn: [finalizerDep2], finalizedBy: [finalizer1])
        Task broken = task("broken", type: Async, finalizedBy: [finalizer1], failure: new RuntimeException())
        Task dep = task("dep", type: Async, dependsOn: [broken])
        Task task = task("task", type: Async, finalizedBy: [finalizer2], dependsOn: [dep])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(task)

        then:
        executionPlan.tasks as List == [broken, dep, task, finalizerDep2, finalizer2, finalizerDep1, finalizer1]
        assertTaskReady(broken)
        assertTaskReady(finalizerDep1)
        assertTaskReadyAndNoMoreToStart(finalizer1)
        assertAllWorkComplete()

        where:
        continueOnFailure << [true, false]
    }

    def "finalizer dependency runs in parallel with finalized task when that dependency is also an entry task"() {
        given:
        Task finalizerDepDep = task("finalizerDepDep", type: Async)
        Task finalizerDep = task("finalizerDep", type: Async, dependsOn: [finalizerDepDep])
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task a = task("a", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(finalizerDep, a)

        then:
        executionPlan.tasks as List == [finalizerDepDep, finalizerDep, a, finalizer]
        assertTasksReady(finalizerDepDep, a)
        assertTaskReady(finalizerDep)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    def "finalizer dependency runs in parallel with finalized task when that dependency is also a later entry task"() {
        given:
        Task finalizerDepDep = task("finalizerDepDep", type: Async)
        Task finalizerDep = task("finalizerDep", type: Async, dependsOn: [finalizerDepDep])
        Task finalizer = task("finalizer", dependsOn: [finalizerDep])
        Task a = task("a", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(a, finalizerDep)

        then:
        executionPlan.tasks as List == [a, finalizerDepDep, finalizerDep, finalizer]
        assertTasksReady(a, finalizerDepDep)
        assertTaskReady(finalizerDep)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21125")
    def "finalizer dependency runs in parallel with finalized task when that dependency is also a dependency of a later entry task"() {
        given:
        Task finalizer = createTask("finalizer")
        Task finalizerDep = task("finalizerDep", type: Async)
        Task a = task("a", type: Async, finalizedBy: [finalizer])
        // Note: this task must be "ordered" greater than the finalizer to trigger the issue
        Task b = task("zz", type: Async, dependsOn: [finalizerDep])
        relationships(finalizer, dependsOn: [finalizerDep, b])

        when:
        addToGraphAndPopulate(a, b)

        then:
        executionPlan.tasks as List == [a, finalizerDep, b, finalizer]
        ordinalGroups == [0, null, 1, 1]
        reachableFromEntryPoint == [true, true, true, false]
        assertTasksReady(a, finalizerDep)
        assertTaskReady(b)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    def "finalizer dependency runs even when finalizer does not run when dependency is also an entry task"() {
        given:
        Task finalizerDepDep = task("finalizerDepDep", type: Async)
        Task finalizerDep = task("finalizerDep", type: Async, dependsOn: [finalizerDepDep])
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task broken = task("broken", type: Async, failure: new RuntimeException())
        Task a = task("a", type: Async, finalizedBy: [finalizer], dependsOn: [broken])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate(finalizerDep, a)

        then:
        executionPlan.tasks as List == [finalizerDepDep, finalizerDep, broken, a, finalizer]
        assertTasksReady(finalizerDepDep, broken)
        assertTaskReady(finalizerDep, true)
        assertAllWorkComplete(true)
    }

    def "finalizer and dependencies are executed even if the finalized task did not run when finalizer is also an entry task"() {
        Task finalizerDependency = task("finalizerDependency", type: Async)
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDependency])
        Task broken = task("broken", type: Async, failure: new RuntimeException("failure"))
        Task finalized = task("finalized", type: Async, dependsOn: [broken], finalizedBy: [finalizer])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate(finalizer, finalized)

        then:
        executionPlan.tasks as List == [broken, finalized, finalizerDependency, finalizer]
        assertTasksReady(broken, finalizerDependency)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    def "finalizer that is a dependency of multiple finalizers and an entry task"() {
        given:
        Task finalizerDep = task("finalizerDep", type: Async)
        Task finalizer = task("finalizer", dependsOn: [finalizerDep])
        Task a = task("a", type: Async, finalizedBy: [finalizer])
        Task finalizerDep2 = task("finalizerDep2", type: Async, dependsOn: [finalizer])
        Task finalizer2 = task("finalizer2", dependsOn: [finalizerDep2])
        Task finalizer3 = task("finalizer3", dependsOn: [finalizer])
        Task b = task("b", type: Async, finalizedBy: [finalizer2])
        Task c = task("c", type: Async, finalizedBy: [finalizer3], dependsOn: [finalizer2])

        when:
        addToGraphAndPopulate(a, b, c)

        then:
        executionPlan.tasks as List == [a, finalizerDep, finalizer, b, finalizerDep2, finalizer2, c, finalizer3]
        assertTasksReady(a, finalizerDep, b)
        assertTaskReady(finalizer)
        assertTaskReady(finalizerDep2)
        assertTaskReady(finalizer2)
        assertTaskReady(c)
        assertTaskReadyAndNoMoreToStart(finalizer3)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21542")
    def "finalizer and its dependencies run after finalized entry task when the entry task fails"() {
        given:
        TaskInternal finalizer = createTask("finalizer")
        TaskInternal finalizerDep = task("finalizerDep", type: Async, finalizedBy: [finalizer])
        // Name must be ordered after the other tasks to trigger the issue
        TaskInternal entryPoint = task("thing", type: Async, finalizedBy: [finalizer], failure: new RuntimeException())
        relationships(finalizer, dependsOn: [finalizerDep])

        when:
        executionPlan.continueOnFailure = continueOnFailure
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDep, finalizer]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizerDep)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()

        where:
        continueOnFailure << [false, true]
    }

    def "finalizer and its dependencies do not run when finalized task fails and is a dependency of the finalizer"() {
        given:
        TaskInternal finalizerDepDep = task("finalizerDepDep", type: Async)
        TaskInternal finalizerDep = task("finalizerDep", type: Async, dependsOn: [finalizerDepDep])
        TaskInternal finalizer = createTask("finalizer")
        TaskInternal entryPoint = task("entry", type: Async, finalizedBy: [finalizer], failure: new RuntimeException())
        relationships(finalizer, dependsOn: [entryPoint, finalizerDep])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDepDep, finalizerDep, finalizer]
        assertTaskReady(entryPoint)
        assertAllWorkComplete()
    }

    def "finalizer does not run when finalized task fails and is a dependency of the finalizer and continue on failure"() {
        given:
        TaskInternal finalizerDepDep = task("finalizerDepDep", type: Async)
        TaskInternal finalizerDep = task("finalizerDep", type: Async, dependsOn: [finalizerDepDep])
        TaskInternal finalizer = createTask("finalizer")
        TaskInternal entryPoint = task("entry", type: Async, finalizedBy: [finalizer], failure: new RuntimeException())
        relationships(finalizer, dependsOn: [entryPoint, finalizerDep])

        when:
        executionPlan.continueOnFailure = true
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDepDep, finalizerDep, finalizer]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizerDepDep, true)
        assertTaskReadyAndNoMoreToStart(finalizerDep)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21000")
    def "finalizer dependency runs after finalized entry task when the latter is finalizer dependency too"() {
        given:
        TaskInternal finalizer = createTask("finalizer")
        TaskInternal finalizerDep = task("finalizerDep", type: Async, finalizedBy: [finalizer])
        TaskInternal entryPoint = task("entryPoint", type: Async, finalizedBy: [finalizer])
        relationships(finalizer, dependsOn: [finalizerDep, entryPoint])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDep, finalizer]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizerDep)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21000")
    def "finalizer dependencies finalized by finalizer of the entry task can run in parallel"() {
        given:
        TaskInternal finalizer = createTask("finalizer")
        TaskInternal finalizerDepA = task("finalizerDepA", type: Async, finalizedBy: [finalizer])
        TaskInternal finalizerDepB = task("finalizerDepB", type: Async, finalizedBy: [finalizer])
        relationships(finalizer, dependsOn: [finalizerDepA, finalizerDepB])
        TaskInternal entryPoint = task("entryPoint", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDepA, finalizerDepB, finalizer]
        assertTaskReady(entryPoint)
        assertTasksReady(finalizerDepA, finalizerDepB)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    def "finalizer can have overlapping finalized nodes and dependencies"() {
        given:
        TaskInternal finalizer = createTask("finalizer")
        TaskInternal finalizerDepA = task("finalizerDepA", type: Async, finalizedBy: [finalizer])
        TaskInternal finalizerDepB = task("finalizerDepB", type: Async)
        relationships(finalizer, dependsOn: [finalizerDepA, finalizerDepB])
        TaskInternal entryPoint = task("entryPoint", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDepA, finalizerDepB, finalizer]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizerDepA)
        assertTaskReady(finalizerDepB)
        assertTaskReadyAndNoMoreToStart(finalizer)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21000")
    def "dependency of finalizers finalizing other finalizer do not start before the latter"() {
        given:
        TaskInternal finFinalizerA = createTask("finFinalizerA", project, Async)
        TaskInternal finFinalizerB = createTask("finFinalizerB", project, Async)
        TaskInternal finalizer = task("finalizer", type: Async, finalizedBy: [finFinalizerA, finFinalizerB])
        TaskInternal finFinalizerDep = task("finFinalizerDep", type: Async, finalizedBy: [finFinalizerA, finFinalizerB])
        relationships(finFinalizerA, dependsOn: [finFinalizerDep, finalizer])
        relationships(finFinalizerB, dependsOn: [finFinalizerDep, finalizer])
        TaskInternal entryPoint = task("entryPoint", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizer, finFinalizerDep, finFinalizerB, finFinalizerA]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizer)
        assertTaskReady(finFinalizerDep)
        assertTasksReadyAndNoMoreToStart(finFinalizerB, finFinalizerA)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21000")
    def "dependency of finalizers finalizing dependency of other finalizer do not start before this dependency"() {
        given:
        TaskInternal finalizerA = createTask("finalizerA", project, Async)
        TaskInternal finalizerB = createTask("finalizerB", project, Async)
        TaskInternal finalizerC = createTask("finalizerC", project, Async)
        TaskInternal finalizerDepA = task("finalizerDepA", type: Async, finalizedBy: [finalizerA, finalizerB, finalizerC])
        TaskInternal finalizerDepBC = task("finalizerDepBC", type: Async, finalizedBy: [finalizerB, finalizerC])
        relationships(finalizerA, dependsOn: [finalizerDepA])
        relationships(finalizerB, dependsOn: [finalizerDepA, finalizerDepBC])
        relationships(finalizerC, dependsOn: [finalizerDepA, finalizerDepBC])
        TaskInternal entryPoint = task("entryPoint", type: Async, finalizedBy: [finalizerA])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDepA, finalizerDepBC, finalizerB, finalizerC, finalizerA]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizerDepA)
        assertTasksReady(finalizerDepBC, finalizerA)
        assertTasksReadyAndNoMoreToStart(finalizerB, finalizerC)
        assertAllWorkComplete()
    }

    def "dependency of finalizers in chain of finalizers are deferred"() {
        given:
        TaskInternal finalizerA = createTask("finalizerA", project, Async)
        TaskInternal finalizerB = createTask("finalizerB", project, Async)
        TaskInternal finalizerC = createTask("finalizerC", project, Async)
        TaskInternal finalizerD = createTask("finalizerD", project, Async)
        TaskInternal finalizerDepA = task("finalizerDepA", type: Async, finalizedBy: [finalizerA, finalizerB])
        TaskInternal finalizerDepB = task("finalizerDepB", type: Async, finalizedBy: [finalizerB, finalizerC])
        TaskInternal finalizerDepC = task("finalizerDepC", type: Async, finalizedBy: [finalizerC, finalizerD])
        TaskInternal finalizerDepD = task("finalizerDepD", type: Async, finalizedBy: [finalizerD])
        relationships(finalizerA, dependsOn: [finalizerDepA])
        relationships(finalizerB, dependsOn: [finalizerDepA, finalizerDepB])
        relationships(finalizerC, dependsOn: [finalizerDepB, finalizerDepC])
        relationships(finalizerD, dependsOn: [finalizerDepC, finalizerDepD])
        TaskInternal entryPoint = task("entryPoint", type: Async, finalizedBy: [finalizerA])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        // TODO - finalizers are incorrectly ordered
        executionPlan.tasks as List == [entryPoint, finalizerDepA, finalizerDepB, finalizerDepC, finalizerDepD, finalizerD, finalizerC, finalizerB, finalizerA]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizerDepA)
        assertTasksReady(finalizerDepB, finalizerA)
        assertTasksReady(finalizerDepC, finalizerB)
        assertTasksReady(finalizerDepD, finalizerC)
        assertTaskReadyAndNoMoreToStart(finalizerD)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21000")
    def "finalizer dependencies reachable from entry task and finalized by the finalizer can run in parallel"() {
        TaskInternal finalizer = createTask("finalizer", project, Async)
        TaskInternal finalizerDepA = task("finalizerDepA", type: Async, finalizedBy: [finalizer])
        TaskInternal finalizerDepB = task("finalizerDepB", type: Async, finalizedBy: [finalizer])
        relationships(finalizer, dependsOn: [finalizerDepA, finalizerDepB])
        TaskInternal entryPoint = task("entryPoint", type: Async, dependsOn: [finalizerDepA, finalizerDepB])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [finalizerDepA, finalizerDepB, finalizer, entryPoint]
        assertTasksReady(finalizerDepA, finalizerDepB)
        assertTasksReadyAndNoMoreToStart(finalizer, entryPoint)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21125")
    def "multiple finalizers can depend on a task that they all finalize"() {
        TaskInternal finalizerA = createTask("finalizerA", project, Async)
        TaskInternal finalizerB = createTask("finalizerB", project, Async)
        TaskInternal finalizerDepDep = task("finalizerDepDep", type: Async, finalizedBy: [finalizerA, finalizerB])
        TaskInternal finalizerDep = task("finalizerDep", type: Async, dependsOn: [finalizerDepDep])
        relationships(finalizerA, dependsOn: [finalizerDep])
        relationships(finalizerB, dependsOn: [finalizerDep])
        TaskInternal entryPoint = task("entryPoint", type: Async, finalizedBy: [finalizerA, finalizerB])

        when:
        addToGraphAndPopulate(entryPoint)

        then:
        executionPlan.tasks as List == [entryPoint, finalizerDepDep, finalizerDep, finalizerB, finalizerA]
        assertTaskReady(entryPoint)
        assertTaskReady(finalizerDepDep)
        assertTaskReady(finalizerDep)
        assertTasksReadyAndNoMoreToStart(finalizerB, finalizerA)
        assertAllWorkComplete()
    }

    def "assigns finalizer and its dependents to highest ordinal group of the finalized tasks"() {
        given:
        Task finalizerDep = task("finalizerDep", type: Async)
        Task finalizer = task("finalizer", type: Async, dependsOn: [finalizerDep])
        Task dep = task("dep", type: Async)
        Task a = task("a", type: Async, dependsOn: [finalizer, dep])
        Task c = task("c", type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(a, c)

        then:
        executionPlan.tasks as List == [dep, c, finalizerDep, finalizer, a]
        ordinalGroups == [0, 1, 0, 1, 1]
        reachableFromEntryPoint == [true, true, true, true, true]
        assertTasksReady(dep, c, finalizerDep)
        assertTaskReady(finalizer)
        assertTaskReadyAndNoMoreToStart(a)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/21975")
    def "handles task failure when a finalizer is a dependency of and finalized by another node"() {
        Task finalizer1 = createTask("finalizer1")
        Task finalizer2 = task("finalizer2", finalizedBy: [finalizer1])
        Task dep = task("dep", failure: new RuntimeException("broken"))
        Task entry = task("entry", finalizedBy: [finalizer2], dependsOn: [dep])
        relationships(finalizer1, dependsOn: [finalizer2])

        when:
        addToGraphAndPopulate(entry)

        then:
        executionPlan.tasks as List == [dep, entry, finalizer2, finalizer1]
        reachableFromEntryPoint == [true, true, false, false]
        assertTaskReady(dep)
        assertAllWorkComplete()
    }

    def "assigns task to first ordinal group it is reachable from when task is entry task multiple times"() {
        given:
        Task finalizer1 = task("finalizer1", type: Async)
        Task finalizer2 = task("finalizer2", type: Async)
        Task a = task("a", type: Async)
        Task b = task("a", type: Async, dependsOn: [a], finalizedBy: [finalizer1])
        Task c = task("c", type: Async, dependsOn: [b], finalizedBy: [finalizer2])

        when:
        addToGraphAndPopulate(b, c, b, c)

        then:
        executionPlan.tasks as List == [a, b, finalizer1, c, finalizer2]
        ordinalGroups == [0, 0, 0, 1, 1]
        reachableFromEntryPoint == [true, true, false, true, false]
        assertTaskReady(a)
        assertTaskReady(b)
        assertTasksReady(finalizer1, c)
        assertTaskReadyAndNoMoreToStart(finalizer2)
        assertAllWorkComplete()
    }

    def "multiple tasks with async work from the same project can run in parallel"() {
        given:
        def foo = task("foo", type: Async)
        def bar = task("bar", type: Async)
        def baz = task("baz", type: Async)

        when:
        addToGraphAndPopulate(foo, bar, baz)

        then:
        assertTasksReadyAndNoMoreToStart(foo, bar, baz)
        assertAllWorkComplete()
    }

    def "one non-async task per project is allowed"() {
        given:
        //2 projects, 2 non parallelizable tasks each
        def projectA = project(project, "a")
        def projectB = project(project, "b")

        def fooA = task("foo", project: projectA)
        def barA = task("bar", project: projectA)

        def fooB = task("foo", project: projectB)
        def barB = task("bar", project: projectB)

        when:
        addToGraphAndPopulate(fooA, barA, fooB, barB)
        def taskNode1 = selectNextTaskNode()
        def taskNode2 = selectNextTaskNode()

        then:
        lockedProjects == [projectA, projectB] as Set
        !taskNode1.task.project.is(taskNode2.task.project)
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(taskNode1)
        finishedExecuting(taskNode2)
        def taskNode3 = selectNextTaskNode()
        def taskNode4 = selectNextTaskNode()

        then:
        lockedProjects == [projectA, projectB] as Set
        !taskNode3.task.project.is(taskNode4.task.project)
    }

    def "a non-async task can start while an async task from the same project is waiting for work to complete"() {
        given:
        def bar = task("bar", type: Async)
        def foo = task("foo")

        when:
        addToGraphAndPopulate(bar, foo)
        def asyncTask = selectNextTask()
        then:
        asyncTask == bar

        when:
        def nonAsyncTask = selectNextTask()

        then:
        nonAsyncTask == foo
    }

    def "an async task does not start while a non-async task from the same project is running"() {
        given:
        def a = task("a")
        def b = task("b", type: Async)

        when:
        addToGraphAndPopulate(a, b)
        def nonAsyncTaskNode = selectNextTaskNode()
        then:
        nonAsyncTaskNode.task == a
        assertNoWorkReadyToStartAfterSelect()
        lockedProjects.size() == 1

        when:
        finishedExecuting(nonAsyncTaskNode)
        def asyncTask = selectNextTask()
        then:
        asyncTask == b
        lockedProjects.empty
    }

    def "two tasks with #relation relationship are not executed in parallel"() {
        given:
        Task a = task("a", type: Async)
        Task b = task("b", type: Async, ("${relation}".toString()): [a])

        when:
        addToGraphAndPopulate(a, b)
        def firstTaskNode = selectNextTaskNode()
        then:
        firstTaskNode.task == a
        assertNoWorkReadyToStart()
        lockedProjects.empty

        when:
        finishedExecuting(firstTaskNode)
        def secondTask = selectNextTask()
        then:
        secondTask == b

        where:
        relation << ["dependsOn", "mustRunAfter"]
    }

    def "two tasks with should run after ordering are executed in parallel"() {
        given:
        def a = task("a", type: Async)
        def b = task("b", type: Async)
        b.shouldRunAfter(a)

        when:
        addToGraphAndPopulate(a, b)

        def firstTask = selectNextTask()
        def secondTask = selectNextTask()
        then:
        firstTask == a
        secondTask == b
    }

    def "task is not available for execution until all of its dependencies that are executed in parallel complete"() {
        given:
        Task a = task("a", type: Async)
        Task b = task("b", type: Async)
        Task c = task("c", type: Async, dependsOn: [a, b])

        when:
        addToGraphAndPopulate(a, b, c)

        def firstTaskNode = selectNextTaskNode()
        def secondTaskNode = selectNextTaskNode()
        then:
        [firstTaskNode, secondTaskNode]*.task as Set == [a, b] as Set
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(firstTaskNode)
        then:
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(secondTaskNode)
        then:
        selectNextTask() == c

    }

    def "two tasks that have the same file in outputs are not executed in parallel"() {
        def sharedFile = file("output")

        given:
        Task a = task("a", type: AsyncWithOutputFile)
        _ * a.outputFile >> sharedFile
        Task b = task("b", type: AsyncWithOutputFile)
        _ * b.outputFile >> sharedFile

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "two tasks that have the same file as output and local state are not executed in parallel"() {
        def sharedFile = file("output")

        given:
        Task a = task("a", type: AsyncWithOutputFile)
        _ * a.outputFile >> sharedFile
        Task b = task("b", type: AsyncWithLocalState)
        _ * b.localStateFile >> sharedFile

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task that writes into a directory that is an output of a running task is not started"() {
        given:
        Task a = task("a", type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("outputDir")
        Task b = task("b", type: AsyncWithOutputDirectory)
        _ * b.outputDirectory >> file("outputDir").file("outputSubdir").file("output")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task that writes into an ancestor directory of a file that is an output of a running task is not started"() {
        given:
        Task a = task("a", type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("outputDir").file("outputSubdir").file("output")
        Task b = task("b", type: AsyncWithOutputDirectory)
        _ * b.outputDirectory >> file("outputDir")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    @ToBeImplemented("When we support symlinks in the VFS, we should implement this as well")
    @Requires(UnitTestPreconditions.Symlinks)
    def "a task that writes into a symlink that overlaps with output of currently running task is not started"() {
        given:
        def taskOutput = file("outputDir").createDir()
        def symlink = file("symlink")
        symlink.createLink(taskOutput)

        and:
        Task a = task("a", type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> taskOutput
        Task b = task("b", type: AsyncWithOutputFile)
        // Need to use new File() here, since TestFile.file() canonicalizes the result
        _ * b.outputFile >> new File(symlink, "fileUnderSymlink")

        expect:
        // TODO: Should be tasksAreNotExecutedInParallel(a, b)
        tasksAreExecutedInParallel(a, b)
    }

    @ToBeImplemented("When we support symlinks in the VFS, we should implement this as well")
    @Requires(UnitTestPreconditions.Symlinks)
    def "a task that writes into a symlink of a shared output dir of currently running task is not started"() {
        given:
        def taskOutput = file("outputDir").createDir()
        def symlink = file("symlink")
        symlink.createLink(taskOutput)

        and:
        Task a = task("a", type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> taskOutput
        Task b = task("b", type: AsyncWithOutputDirectory)
        _ * b.outputDirectory >> symlink

        expect:
        // TODO: Should be: tasksAreNotExecutedInParallel(a, b)
        tasksAreExecutedInParallel(a, b)

        cleanup:
        assert symlink.delete()
    }

    @ToBeImplemented("When we support symlinks in the VFS, we should implement this as well")
    @Requires(UnitTestPreconditions.Symlinks)
    def "a task that stores local state into a symlink of a shared output dir of currently running task is not started"() {
        given:
        def taskOutput = file("outputDir").createDir()
        def symlink = file("symlink")
        symlink.createLink(taskOutput)

        and:
        Task a = task("a", type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> taskOutput
        Task b = task("b", type: AsyncWithLocalState)
        _ * b.localStateFile >> symlink

        expect:
        // TODO: Should be: tasksAreNotExecutedInParallel(a, b)
        tasksAreExecutedInParallel(a, b)

        cleanup:
        assert symlink.delete()
    }

    def "tasks from two different projects that have the same file in outputs are not executed in parallel"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputFile)
        _ * a.outputFile >> file("output")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithOutputFile)
        _ * b.outputFile >> file("output")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task from different project that writes into a directory that is an output of currently running task is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("outputDir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithOutputFile)
        _ * b.outputFile >> file("outputDir").file("outputSubdir").file("output")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task that destroys a directory that is an output of a currently running task is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("outputDir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("outputDir")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task that writes to a directory that is being destroyed by a currently running task is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithDestroysFile)
        _ * a.destroysFile >> file("outputDir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithOutputDirectory)
        _ * b.outputDirectory >> file("outputDir")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task that destroys an ancestor directory of an output of a currently running task is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("outputDir").file("outputSubdir").file("output")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("outputDir")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task that writes to an ancestor of a directory that is being destroyed by a currently running task is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithDestroysFile)
        _ * a.destroysFile >> file("outputDir").file("outputSubdir").file("output")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithOutputDirectory)
        _ * b.outputDirectory >> file("outputDir")

        expect:
        tasksAreNotExecutedInParallel(a, b)
    }

    def "a task that destroys an intermediate input is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("inputDir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("inputDir")
        def projectC = project(project, "c")
        Task c = task("c", project: projectC, type: AsyncWithInputDirectory, dependsOn: [a])
        _ * c.inputDirectory >> file("inputDir")

        file("inputDir").file("inputSubdir").file("foo").file("bar") << "bar"

        expect:
        destroyerRunsLast(a, c, b)
    }

    private void destroyerRunsLast(Task producer, Task consumer, Task destroyer) {
        addToGraphAndPopulate(producer, destroyer, consumer)

        def producerInfo = selectNextTaskNode()

        assert producerInfo.task == producer
        assertNoTaskReadyToStart()

        finishedExecuting(producerInfo)
        def consumerInfo = selectNextTaskNode()

        assert consumerInfo.task == consumer
        assertNoTaskReadyToStart()

        finishedExecuting(consumerInfo)
        def destroyerInfo = selectNextTaskNode()

        assert destroyerInfo.task == destroyer
    }

    def "a task that destroys an ancestor of an intermediate input is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("inputDir").file("inputSubdir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("inputDir")
        def projectC = project(project, "c")
        Task c = task("c", project: projectC, type: AsyncWithInputDirectory, dependsOn: [a])
        _ * c.inputDirectory >> file("inputDir").file("inputSubdir")

        file("inputDir").file("inputSubdir").file("foo").file("bar") << "bar"

        expect:
        destroyerRunsLast(a, c, b)
    }

    def "a task that destroys a descendant of an intermediate input is not started"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("inputDir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("inputDir").file("inputSubdir").file("foo")
        def projectC = project(project, "c")
        Task c = task("c", project: projectC, type: AsyncWithInputDirectory, dependsOn: [a])
        _ * c.inputDirectory >> file("inputDir")

        file("inputDir").file("inputSubdir").file("foo").file("bar") << "bar"

        expect:
        destroyerRunsLast(a, c, b)
    }

    def "a task that destroys an intermediate input can be started if it's ordered first"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("inputDir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("inputDir")
        def projectC = project(project, "c")
        Task c = task("c", project: projectC, type: AsyncWithInputDirectory, dependsOn: [a])
        _ * c.inputDirectory >> file("inputDir")

        file("inputDir").file("inputSubdir").file("foo").file("bar") << "bar"

        expect:
        destroyerRunsFirst(a, c, b)
    }

    private void destroyerRunsFirst(Task producer, Task consumer, Task destroyer) {
        addToGraph(destroyer)
        addToGraphAndPopulate(producer, consumer)

        def destroyerInfo = selectNextTaskNode()

        assert destroyerInfo.task == destroyer
        assertNoTaskReadyToStart()

        finishedExecuting(destroyerInfo)
        def producerInfo = selectNextTaskNode()

        assert producerInfo.task == producer
        assertNoTaskReadyToStart()

        finishedExecuting(producerInfo)
        def consumerInfo = selectNextTaskNode()

        assert consumerInfo.task == consumer
    }

    def "a task that destroys an ancestor of an intermediate input can be started if it's ordered first"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("inputDir").file("inputSubdir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("inputDir")
        def projectC = project(project, "c")
        Task c = task("c", project: projectC, type: AsyncWithInputDirectory, dependsOn: [a])
        _ * c.inputDirectory >> file("inputDir").file("inputSubdir")

        file("inputDir").file("inputSubdir").file("foo").file("bar") << "bar"

        expect:
        destroyerRunsFirst(a, c, b)
    }

    def "a task that destroys a descendant of an intermediate input can be started if it's ordered first"() {
        given:
        def projectA = project(project, "a")
        Task a = task("a", project: projectA, type: AsyncWithOutputDirectory)
        _ * a.outputDirectory >> file("inputDir")
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: AsyncWithDestroysFile)
        _ * b.destroysFile >> file("inputDir").file("inputSubdir").file("foo")
        def projectC = project(project, "c")
        Task c = task("c", project: projectC, type: AsyncWithInputDirectory, dependsOn: [a])
        _ * c.inputDirectory >> file("inputDir")

        file("inputDir").file("inputSubdir").file("foo").file("bar") << "bar"

        expect:
        destroyerRunsFirst(a, c, b)
    }

    def "a task that destroys the output of a task and has a dependency in another project runs first if it is ordered first"() {
        given:
        def projectA = project(project, "a")
        Task producer = task("producer", project: projectA, type: AsyncWithOutputDirectory)
        _ * producer.outputDirectory >> file("inputDir")
        def projectC = project(project, "c")
        Task dependency = task("dependency", project: projectC, type: AsyncWithDestroysFile)
        _ * dependency.destroysFile >> file("someOtherDir")
        def projectB = project(project, "b")
        Task destroyer = task("destroyer", project: projectB, type: AsyncWithDestroysFile, dependsOn: [dependency])
        _ * destroyer.destroysFile >> file("inputDir").file("inputSubdir").file("foo")

        when:
        addToGraph(destroyer)
        addToGraphAndPopulate(producer)

        then:
        def dependencyNode = selectNextTaskNode()
        dependencyNode.task == dependency
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(dependencyNode)

        then:
        def destroyerNode = selectNextTaskNode()
        destroyerNode.task == destroyer
        assertNoTaskReadyToStart()

        when:
        finishedExecuting(destroyerNode)

        then:
        selectNextTask() == producer
    }

    def "producer ordered before destroyer on command-line overrides conflicting shouldRunAfter relationship"() {
        given:
        Task destroyer = task("destroyer", type: AsyncWithDestroysFile)
        _ * destroyer.destroysFile >> file("inputDir")
        Task producer = task("producer", type: AsyncWithOutputDirectory, shouldRunAfter: [destroyer])
        _ * producer.outputDirectory >> file("inputDir")

        when:
        addToGraphAndPopulate(producer, destroyer)

        then:
        // TODO - this is the wrong order (the order of this list does not take ordinal groups into account)
        executionPlan.tasks as List == [destroyer, producer]
        ordinalGroups == [1, 0]
        assertTaskReady(producer, true)
        assertTaskReadyAndNoMoreToStart(destroyer)
        assertAllWorkComplete()
    }

    def "destroyer ordered before producer on command-line overrides conflicting shouldRunAfter relationship"() {
        given:
        Task producer = task("producer", type: AsyncWithOutputDirectory)
        _ * producer.outputDirectory >> file("inputDir")
        Task destroyer = task("destroyer", type: AsyncWithDestroysFile, shouldRunAfter: [producer])
        _ * destroyer.destroysFile >> file("inputDir")

        when:
        addToGraphAndPopulate(destroyer, producer)

        then:
        // TODO - this is the wrong order (the order of this list does not take ordinal groups into account)
        executionPlan.tasks as List == [producer, destroyer]
        ordinalGroups == [1, 0]
        assertTaskReady(destroyer, true)
        assertTaskReadyAndNoMoreToStart(producer)
        assertAllWorkComplete()
    }

    def "non-conflicting producers in all later groups can start once destroyer is complete"() {
        given:
        Task producer1 = task("producer1", type: AsyncWithOutputDirectory)
        _ * producer1.outputDirectory >> file("dir1")
        Task producer2 = task("producer2", type: AsyncWithOutputDirectory)
        _ * producer2.outputDirectory >> file("dir2")
        Task destroyer = task("destroyer", type: AsyncWithDestroysFile)
        _ * destroyer.destroysFile >> file("dir3")

        when:
        addToGraphAndPopulate(destroyer, producer1, producer2)

        then:
        executionPlan.tasks as List == [destroyer, producer1, producer2]
        ordinalGroups == [0, 1, 2]
        assertLastTaskOfGroupReady(destroyer)
        assertTasksReadyAndNoMoreToStart(producer1, producer2)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/20559")
    def "a task that destroys the output of a task in another project runs first if it is ordered first"() {
        given:
        def projectA = project(project, "a")
        Task producer = task("producer", project: projectA, type: AsyncWithOutputDirectory)
        _ * producer.outputDirectory >> file("inputDir")
        def projectB = project(project, "b")
        Task destroyer = task("destroyer", project: projectB, type: AsyncWithDestroysFile)
        _ * destroyer.destroysFile >> file("inputDir")

        when:
        addToGraph(destroyer)
        addToGraphAndPopulate(producer)
        def node1 = selectNextNode()

        then:
        assertIsResolveMutationsOf(node1, destroyer)
        assertNoWorkReadyToStart()

        when:
        node1.execute()
        finishedExecuting(node1)
        def node2 = selectNextNode()
        def node3 = selectNextNode()

        then:
        node2.task == destroyer
        node3 instanceof OrdinalNode && node3.type == OrdinalNode.Type.DESTROYER
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(node3)
        def node4 = selectNextNode()

        then:
        assertIsResolveMutationsOf(node4, producer)
        assertNoWorkReadyToStart()

        when:
        node4.execute()
        finishedExecuting(node4)

        then:
        assertNoWorkReadyToStartAfterSelect() // destroyer is still running, so producer cannot start

        when:
        finishedExecuting(node2)

        then:
        assertTaskReadyAndNoMoreToStart(producer)
        assertAllWorkComplete()
    }

    void assertIsResolveMutationsOf(Node node, Task task) {
        def taskNode = taskNodeFactory.getNode(task)
        assert node == taskNode.prepareNode
    }

    def "a task that produces an output and has a dependency in another project runs first if it is ordered first"() {
        given:
        def projectC = project(project, "c")
        Task dependency = task("dependency", project: projectC, type: AsyncWithOutputDirectory)
        _ * dependency.outputDirectory >> file("someOtherDir")
        def projectA = project(project, "a")
        Task producer = task("producer", project: projectA, type: AsyncWithOutputDirectory, dependsOn: [dependency])
        _ * producer.outputDirectory >> file("inputDir")
        def projectB = project(project, "b")
        Task destroyer = task("destroyer", project: projectB, type: AsyncWithDestroysFile)
        _ * destroyer.destroysFile >> file("inputDir").file("inputSubdir").file("foo")

        when:
        addToGraph(producer)
        addToGraphAndPopulate(destroyer)

        then:
        def dependencyNode = selectNextTaskNode()
        dependencyNode.task == dependency
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(dependencyNode)

        then:
        def producerNode = selectNextTaskNode()
        producerNode.task == producer
        assertNoTaskReadyToStart()

        when:
        finishedExecuting(producerNode)

        then:
        selectNextTask() == destroyer
    }

    @Issue("https://github.com/gradle/gradle/issues/8253")
    def "dependency of dependency of finalizer is scheduled when another task depends on the dependency"() {
        given:
        Task dependencyOfDependency = task("dependencyOfDependency", type: Async)
        Task dependency = task("dependency", type: Async, dependsOn: [dependencyOfDependency])
        Task finalizer = task("finalizer", type: Async, dependsOn: [dependency])
        Task finalized = task("finalized", type: Async, finalizedBy: [finalizer])
        Task otherTaskWithDependency = task("otherTaskWithDependency", type: Async, dependsOn: [dependency])

        when:
        addToGraph(finalized)
        addToGraph(otherTaskWithDependency)
        populateGraph()

        and:
        def finalizedNode = selectNextTaskNode()
        def dependencyOfDependencyNode = selectNextTaskNode()
        then:
        finalizedNode.task == finalized
        dependencyOfDependencyNode.task == dependencyOfDependency
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(dependencyOfDependencyNode)
        def dependencyNode = selectNextTaskNode()
        then:
        dependencyNode.task == dependency
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(dependencyNode)
        def otherTaskWithDependencyNode = selectNextTaskNode()
        then:
        otherTaskWithDependencyNode.task == otherTaskWithDependency
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(otherTaskWithDependencyNode)
        then:
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(finalizedNode)
        then:
        selectNextTask() == finalizer
        assertNoMoreWorkToStartButNotAllComplete()
    }

    def "must run after is respected for finalizers"() {
        Task dependency = task("dependency", type: Async)
        Task finalizer = task("finalizer", type: Async)
        Task finalized = task("finalized", type: Async, dependsOn: [dependency], finalizedBy: [finalizer])
        Task mustRunAfter = task("mustRunAfter", type: Async, mustRunAfter: [finalizer])

        when:
        addToGraph(finalized)
        addToGraph(mustRunAfter)
        populateGraph()

        and:
        def node1 = selectNextTaskNode()

        then:
        assertNoWorkReadyToStart()
        node1.task == dependency

        when:
        finishedExecuting(node1)
        def node2 = selectNextTaskNode()

        then:
        assertNoWorkReadyToStart()
        node2.task == finalized

        when:
        finishedExecuting(node2)
        def node3 = selectNextTaskNode()

        then:
        assertNoWorkReadyToStart()
        node3.task == finalizer

        when:
        finishedExecuting(node3)
        def node4 = selectNextTaskNode()

        then:
        assertNoMoreWorkToStartButNotAllComplete()
        node4.task == mustRunAfter
    }

    def "handles an exception while walking the task graph when an enforced task is present"() {
        given:
        Task finalizer = task("finalizer", type: BrokenTask)
        _ * finalizer.outputFiles >> { throw new RuntimeException("broken") }
        Task finalized = task("finalized", finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(finalized)
        def finalizedNode = selectNextTaskNode()

        then:
        finalizedNode.task == finalized
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(finalizedNode)
        def node = selectNextNode()
        node.execute()
        finishedExecuting(node)

        then:
        assertAllWorkComplete()

        when:
        def failures = []
        coordinator.withStateLock {
            finalizedPlan.collectFailures(failures)
        }

        then:
        failures.size() == 1
        def e = failures.first()
        e.message.contains("Execution failed for task :finalizer")

        then:
        coordinator.withStateLock {
            finalizedPlan.contents.getNode(finalized).isSuccessful()
            finalizedPlan.contents.getNode(finalizer).state == Node.ExecutionState.FAILED_DEPENDENCY
        }
    }

    def "no task is started when invalid task is running"() {
        given:
        def first = task("first", type: Async)
        def second = task("second", type: Async)

        when:
        addToGraphAndPopulate(first, second)
        def invalidTaskNode = selectNextTaskNode()

        then:
        invalidTaskNode.task == first
        1 * nodeValidator.hasValidationProblems({ LocalTaskNode node -> node.task == first }) >> true
        0 * nodeValidator.hasValidationProblems(_ as Node)
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(invalidTaskNode)
        def validTask = selectNextTask()
        then:
        validTask == second
    }

    def "an invalid task is not started when another task is running"() {
        given:
        def first = task("first", type: Async)
        def second = task("second", type: Async)

        when:
        addToGraphAndPopulate(first, second)
        def validTaskNode = selectNextTaskNode()

        then:
        validTaskNode.task == first
        1 * nodeValidator.hasValidationProblems({ LocalTaskNode node -> node.task == first }) >> false
        0 * nodeValidator.hasValidationProblems(_ as Node)

        when:
        assertNoTaskReadyToStart()

        then:
        1 * nodeValidator.hasValidationProblems({ LocalTaskNode node -> node.task == second }) >> true
        0 * nodeValidator.hasValidationProblems(_ as Node)

        when:
        finishedExecuting(validTaskNode)
        def invalidTask = selectNextTask()
        then:
        invalidTask == second
    }

    @Issue("https://github.com/gradle/gradle/issues/22320")
    def "task in an included build can depend on a finalizer dependency in an earlier ordinal group"() {
        given:
        def commonDep = task("commonDep", type: Async)
        def finalizer1 = task("finalizer1", type: Async, dependsOn: [commonDep])
        def finalizer2 = task("finalizer2", type: Async, dependsOn: [commonDep])
        def entry1 = task("entry1", type: Async, finalizedBy: [finalizer1, finalizer2])
        def entry2 = task("entry2", type: Async, dependsOn: [commonDep])

        when:
        addToGraph(entry1)
        executionPlan.determineExecutionPlan() // this is called between entry groups for an included build (but not the root build) and this call triggers the issue
        addToGraph(entry2)

        then:
        noExceptionThrown()
    }

    def "runs priority node before other nodes even when scheduled later"() {
        def node = priorityNode()
        def task = task("task")

        when:
        addToGraph(task)
        addToGraph(node) // must be scheduled after the task
        populateGraph()

        def first = selectNextNode()
        def second = selectNextTaskNode()

        then:
        first == node
        second.task == task
        assertNoMoreWorkToStartButNotAllComplete()

        when:
        finishedExecuting(second)
        finishedExecuting(first)

        then:
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/20508")
    def "stops executing nodes after failure when priority node has already executed"() {
        def node = priorityNode()
        def broken = task("broken", failure: new RuntimeException())
        def task = task("task")

        when:
        addToGraph(broken, task)
        addToGraph(node) // must be scheduled after the broken task
        populateGraph()

        def first = selectNextNode()

        then:
        first == node

        when:
        finishedExecuting(first)

        then:
        assertNextTaskReady(broken)
        assertAllWorkComplete()
    }

    @Issue("https://github.com/gradle/gradle/issues/20508")
    def "stops executing nodes after failure while priority node is executing"() {
        def node = priorityNode()
        def broken = task("broken", failure: new RuntimeException())
        def task = task("task")

        when:
        addToGraph(broken, task)
        addToGraph(node) // must be scheduled after the broken task
        populateGraph()

        def first = selectNextNode()
        def second = selectNextTaskNode()

        then:
        first == node
        second.task == broken

        when:
        finishedExecuting(second)
        finishedExecuting(first)

        then:
        assertAllWorkComplete()
    }

    def "stops executing nodes after priority node fails"() {
        def node = priorityNode(failure: new RuntimeException())
        def task = task("task")

        when:
        addToGraph(task)
        addToGraph(node) // must be scheduled after tasks
        populateGraph()

        def first = selectNextNode()

        then:
        first == node

        when:
        finishedExecuting(first)

        then:
        assertAllWorkComplete()
    }

    def "node can provide additional dependencies immediately before execution"() {
        def dep = node("dep")
        def preNode1 = node("preA")
        def preNode2 = node("preB")
        def target = node("target", dependsOn: dep, preNodes: [preNode1, preNode2])
        def entry = node("entry", dependsOn: target)

        when:
        addToGraph(dep, target, entry)
        populateGraph()

        then:
        scheduledNodes == [dep, target, entry]
        assertNodeReady(dep)
        assertNodesReady(preNode1, preNode2)
        assertNodeReady(target)
        assertNodeReadyAndNoMoreToStart(entry)
        assertAllWorkComplete()
    }

    def "node can provide pre-execution dependency that is already scheduled"() {
        def dep = node("dep")
        def preNode1 = node("pre1", dependsOn: dep)
        def preNode2 = node("pre2")
        def target = node("target", preNodes: [preNode1, preNode2])
        def entry = node("entry", dependsOn: [preNode1, target])

        when:
        addToGraph(dep, target, preNode1, entry)
        populateGraph()

        then:
        scheduledNodes == [dep, target, preNode1, entry]
        assertNodesReady(dep, preNode2)
        assertNodeReady(preNode1)
        assertNodeReady(target)
        assertNodeReadyAndNoMoreToStart(entry)
        assertAllWorkComplete()
    }

    def "does not run node whose pre-execution dependency fails"() {
        def dep = node("dep")
        def preNode1 = node("pre1")
        def preNode2 = node("pre2", failure: new RuntimeException())
        def target = node("target", dependsOn: dep, preNodes: [preNode1, preNode2])
        def entry = node("entry", dependsOn: target)

        when:
        executionPlan.continueOnFailure = continueOnFailure
        addToGraph(dep, target, entry)
        populateGraph()

        then:
        scheduledNodes == [dep, target, entry]
        assertNodeReady(dep)
        assertNodesReady(preNode1, preNode2)
        assertAllWorkComplete(continueOnFailure)

        where:
        continueOnFailure << [false, true]
    }

    def "does not run pre-execution dependencies when some other dependency fails"() {
        def dep = node("dep", failure: new RuntimeException())
        def preNode1 = node("pre1")
        def preNode2 = node("pre2")
        def target = node("target", dependsOn: dep, preNodes: [preNode1, preNode2])
        def entry = node("entry", dependsOn: target)

        when:
        executionPlan.continueOnFailure = continueOnFailure
        addToGraph(dep, target, entry)
        populateGraph()

        then:
        scheduledNodes == [dep, target, entry]
        assertNodeReady(dep)
        assertAllWorkComplete(continueOnFailure)

        where:
        continueOnFailure << [false, true]
    }

    def "node can provide additional nodes to run immediately after execution"() {
        def postNode1 = node("post1")
        def postNode2 = node("post2")
        def target = node("target", postNodes: [postNode1, postNode2])
        def entry = node("entry", dependsOn: target)

        when:
        addToGraph(target, entry)
        populateGraph()

        then:
        scheduledNodes == [target, entry]
        assertNodeReady(target)
        assertNodesReady(postNode1, postNode2)
        assertNodeReadyAndNoMoreToStart(entry)
        assertAllWorkComplete()
    }

    def "node can provide post-execution node that is already scheduled"() {
        def postNode1 = node("post1")
        def postNode2 = node("post2", dependsOn: postNode1)
        def target = node("target", postNodes: [postNode1, postNode2])
        def entry = node("entry", dependsOn: [postNode1, target])

        when:
        addToGraph(target, postNode1, entry)
        populateGraph()

        then:
        scheduledNodes == [target, postNode1, entry]
        assertNodesReady(target, postNode1)
        assertNodeReady(postNode2)
        assertNodeReadyAndNoMoreToStart(entry)
        assertAllWorkComplete()
    }

    def "does not run dependent nodes when post-execution node fails"() {
        def postNode1 = node("post1")
        def postNode2 = node("post2", failure: new RuntimeException())
        def target = node("target", postNodes: [postNode1, postNode2])
        def entry = node("entry", dependsOn: target)

        when:
        executionPlan.continueOnFailure = continueOnFailure
        addToGraph(target, entry)
        populateGraph()

        then:
        scheduledNodes == [target, entry]
        assertNodeReady(target)
        assertNodesReady(postNode1, postNode2)
        assertAllWorkComplete(continueOnFailure)

        where:
        continueOnFailure << [false, true]
    }

    def "does not run post-execution nodes when node fails"() {
        def postNode1 = node("post1")
        def postNode2 = node("post2")
        def target = node("target", postNodes: [postNode1, postNode2], failure: new RuntimeException())
        def entry = node("entry", dependsOn: target)

        when:
        executionPlan.continueOnFailure = continueOnFailure
        addToGraph(target, entry)
        populateGraph()

        then:
        scheduledNodes == [target, entry]
        assertNodeReady(target)
        assertAllWorkComplete(continueOnFailure)

        where:
        continueOnFailure << [false, true]
    }

    private void tasksAreNotExecutedInParallel(Task first, Task second) {
        addToGraphAndPopulate(first, second)

        def firstTaskNode = selectNextTaskNode()

        assertNoTaskReadyToStart()
        assert lockedProjects.empty

        finishedExecuting(firstTaskNode)
        def secondTask = selectNextTask()

        assert [firstTaskNode.task, secondTask] as Set == [first, second] as Set
    }

    private void tasksAreExecutedInParallel(Task first, Task second) {
        addToGraphAndPopulate(first, second)

        def tasks = [selectNextTask(), selectNextTask()]

        assert tasks as Set == [first, second] as Set
    }

    private void addToGraph(Task... tasks) {
        for (final def task in tasks) {
            executionPlan.addEntryTask(task)
        }
    }

    private void addToGraph(Node... nodes) {
        for (final def node in nodes) {
            executionPlan.addEntryNodes([node])
        }
    }

    private void addToGraphAndPopulate(Task... tasks) {
        addToGraph(tasks)
        populateGraph()
    }

    private void populateGraph() {
        executionPlan.determineExecutionPlan()
        finalizedPlan = executionPlan.finalizePlan()
    }

    TestFile file(String path) {
        temporaryFolder.file(path)
    }

    static class Async extends DefaultTask {}

    static class AsyncWithOutputFile extends Async {
        @OutputFile
        File outputFile
    }

    static class AsyncWithOutputDirectory extends Async {
        @OutputDirectory
        File outputDirectory
    }

    static class AsyncWithDestroysFile extends Async {
        @Destroys
        File destroysFile
    }

    static class AsyncWithLocalState extends Async {
        @LocalState
        File localStateFile
    }

    static class AsyncWithInputFile extends Async {
        @InputFile
        File inputFile
    }

    static class AsyncWithInputDirectory extends Async {
        @InputDirectory
        File inputDirectory
    }

    static class BrokenTask extends DefaultTask {
        @OutputFiles
        FileCollection getOutputFiles() {
            throw new Exception("BOOM!")
        }
    }

    static class FailingTask extends DefaultTask {
        @TaskAction
        void execute() {
            throw new RuntimeException("BOOM!")
        }
    }

    /**
     * Asserts there is a task ready to run, and runs it.
     */
    void assertNextTaskReady(Task task) {
        def node = selectNextTaskNode()
        assert node.task == task
        assertWorkReadyToStart()
        finishedExecuting(node)
    }

    void assertTaskReady(Task task, boolean needToSelect = false) {
        def node = selectNextTaskNode()
        assert node.task == task
        if (needToSelect) {
            assertNoWorkReadyToStartAfterSelect()
        } else {
            assertNoWorkReadyToStart()
        }
        finishedExecuting(node)
    }

    void assertNodeReady(Node expected, boolean needToSelect = false) {
        def node = selectNextNode()
        assert node == expected
        if (needToSelect) {
            assertNoWorkReadyToStartAfterSelect()
        } else {
            assertNoWorkReadyToStart()
        }
        finishedExecuting(node)
    }

    void assertLastTaskOfGroupReady(Task task, boolean needToSelect = false) {
        def node = selectNextTaskNode()
        assert node.task == task
        def ordinalNode = selectNextNode()
        assert ordinalNode instanceof OrdinalNode
        if (needToSelect) {
            assertNoWorkReadyToStartAfterSelect()
        } else {
            assertNoWorkReadyToStart()
        }
        finishedExecuting(node)
        assertNoWorkReadyToStart()
        finishedExecuting(ordinalNode)
    }

    void assertTaskReadyAndNoMoreToStart(Task task, boolean needToSelect = false) {
        def node = selectNextTaskNode()
        assert node.task == task
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node)
    }

    void assertNodeReadyAndNoMoreToStart(Node expected, boolean needToSelect = false) {
        def node = selectNextNode()
        assert node == expected
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node)
    }

    void assertTasksReady(Task task1, Task task2, boolean needToSelect = false) {
        def node1 = selectNextTaskNode()
        assert node1.task == task1
        def node2 = selectNextTaskNode()
        assert node2.task == task2
        if (needToSelect) {
            assertNoWorkReadyToStartAfterSelect()
        } else {
            assertNoWorkReadyToStart()
        }
        finishedExecuting(node2)
        finishedExecuting(node1)
    }

    void assertNodesReady(Node expected1, Node expected2, boolean needToSelect = false) {
        def node1 = selectNextNode()
        assert node1 == expected1
        def node2 = selectNextNode()
        assert node2 == expected2
        if (needToSelect) {
            assertNoWorkReadyToStartAfterSelect()
        } else {
            assertNoWorkReadyToStart()
        }
        finishedExecuting(node2)
        finishedExecuting(node1)
    }

    void assertTasksReadyAndNoMoreToStart(Task task1, Task task2, boolean needToSelect = false) {
        def node1 = selectNextTaskNode()
        assert node1.task == task1
        def node2 = selectNextTaskNode()
        assert node2.task == task2
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node2)
        assertNoMoreWorkToStartButNotAllComplete(false)
        finishedExecuting(node1)
    }


    void assertTasksReadyAndNoMoreToStart(Task task1, Task task2, Task task3, boolean needToSelect = false) {
        def node1 = selectNextTaskNode()
        assert node1.task == task1
        def node2 = selectNextTaskNode()
        assert node2.task == task2
        def node3 = selectNextTaskNode()
        assert node3.task == task3
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node3)
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node2)
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node1)
    }

    void assertTasksReady(Task task1, Task task2, Task task3) {
        def node1 = selectNextTaskNode()
        assert node1.task == task1
        def node2 = selectNextTaskNode()
        assert node2.task == task2
        def node3 = selectNextTaskNode()
        assert node3.task == task3
        assertNoWorkReadyToStart()
        finishedExecuting(node3)
        finishedExecuting(node2)
        finishedExecuting(node1)
    }

    private void finishedExecuting(Node node) {
        coordinator.withStateLock {
            finalizedPlan.finishedExecuting(node, null)
        }
    }

    private List<Integer> getOrdinalGroups() {
        return executionPlan.tasks.collect { taskNodeFactory.getNode(it).group.asOrdinal()?.ordinal }
    }

    private List<Boolean> getReachableFromEntryPoint() {
        return executionPlan.tasks.collect { taskNodeFactory.getNode(it).group.reachableFromEntryPoint }
    }

    private TaskInternal selectNextTask() {
        selectNextTaskNode().task
    }

    private LocalTaskNode selectNextTaskNode() {
        def result = null
        coordinator.withStateLock {
            def node = selectNextNode()
            // ignore nodes that aren't tasks
            if (!(node instanceof LocalTaskNode)) {
                if (node instanceof SelfExecutingNode) {
                    node.execute(null)
                }
                finalizedPlan.finishedExecuting(node, null)
                result = selectNextTaskNode()
                return
            }
            result = node
        }
        return result
    }

    private Node selectNextNode() {
        def result = null
        coordinator.withStateLock {
            WorkSource.Selection selection
            assert !finalizedPlan.allExecutionComplete()
            assert finalizedPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            recordLocks {
                selection = finalizedPlan.selectNext()
            }
            assert !selection.noMoreWorkToStart && !selection.noWorkReadyToStart
            assert !finalizedPlan.allExecutionComplete()
            def nextNode = selection.item
            if (nextNode instanceof LocalTaskNode && nextNode.task instanceof Async) {
                nextNode.projectToLock.unlock()
            }
            result = nextNode
        }
        return result
    }

    void assertNoTaskReadyToStart() {
        coordinator.withStateLock {
            while (finalizedPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart) {
                def selection = finalizedPlan.selectNext()
                if (selection.noWorkReadyToStart) {
                    break
                }
                assert !selection.noMoreWorkToStart
                def node = selection.item
                assert !(node instanceof LocalTaskNode)
                if (node instanceof SelfExecutingNode) {
                    node.execute(null)
                }
                finalizedPlan.finishedExecuting(node, null)
            }
            assert finalizedPlan.executionState() == WorkSource.State.NoWorkReadyToStart
            assert finalizedPlan.selectNext().noWorkReadyToStart
            assert finalizedPlan.executionState() == WorkSource.State.NoWorkReadyToStart
        }
    }

    void assertNoMoreWorkToStartButNotAllComplete(boolean needToSelect = false) {
        coordinator.withStateLock {
            if (needToSelect) {
                assert finalizedPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            } else {
                assert finalizedPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            }
            assert finalizedPlan.selectNext().noMoreWorkToStart
            assert finalizedPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert !finalizedPlan.allExecutionComplete()
        }
    }

    void assertAllWorkComplete(boolean needToSelect = false) {
        coordinator.withStateLock {
            if (needToSelect) {
                assert finalizedPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            } else {
                assert finalizedPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            }
            assert finalizedPlan.selectNext().noMoreWorkToStart
            assert finalizedPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert finalizedPlan.allExecutionComplete()
        }
    }

    void assertNoWorkReadyToStart() {
        coordinator.withStateLock {
            assert finalizedPlan.executionState() == WorkSource.State.NoWorkReadyToStart
            assert finalizedPlan.selectNext().noWorkReadyToStart
            assert finalizedPlan.executionState() == WorkSource.State.NoWorkReadyToStart
        }
    }

    void assertNoWorkReadyToStartAfterSelect() {
        coordinator.withStateLock {
            // In some cases, a call to selectNext() is required to calculate that nothing is ready
            assert finalizedPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            assert finalizedPlan.selectNext().noWorkReadyToStart
            assert finalizedPlan.executionState() == WorkSource.State.NoWorkReadyToStart
        }
    }

    void assertWorkReadyToStart() {
        coordinator.withStateLock {
            assert finalizedPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
        }
    }

    List<Node> getScheduledNodes() {
        def result = []
        executionPlan.scheduledNodes.visitNodes(nodes -> result.addAll(nodes))
        return result
    }

    private static class TestNode extends CreationOrderedNode implements SelfExecutingNode {
        final Throwable failure
        final String name
        final List<Node> preExecuteNodes
        final List<Node> postExecuteNodes

        TestNode(String name, List<Node> dependencies, List<Node> preExecuteNodes, List<Node> postExecuteNodes, @Nullable Throwable failure = null) {
            this.postExecuteNodes = postExecuteNodes
            this.preExecuteNodes = preExecuteNodes
            this.name = name
            this.failure = failure
            for (final def dependency in dependencies) {
                addDependencySuccessor(dependency)
            }
        }

        @Override
        Throwable getNodeFailure() {
            return failure
        }

        @Override
        boolean hasPendingPreExecutionNodes() {
            return !preExecuteNodes.isEmpty()
        }

        @Override
        void visitPreExecutionNodes(Consumer<? super Node> visitor) {
            for (final node in preExecuteNodes) {
                visitor.accept(node)
            }
            preExecuteNodes.clear()
        }

        @Override
        void visitPostExecutionNodes(Consumer<? super Node> visitor) {
            for (final node in postExecuteNodes) {
                visitor.accept(node)
            }
        }

        @Override
        void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        }

        @Override
        String toString() {
            return name
        }

        @Override
        void execute(NodeExecutionContext context) {
        }
    }

    private static class TestPriorityNode extends TestNode implements SelfExecutingNode {
        TestPriorityNode(@Nullable Throwable failure) {
            super("test node", [], [], [], failure)
        }

        @Override
        boolean isPriority() {
            return true
        }
    }
}
