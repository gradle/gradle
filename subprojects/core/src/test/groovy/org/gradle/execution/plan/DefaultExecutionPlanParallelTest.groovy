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
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.Path
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import javax.annotation.Nullable

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class DefaultExecutionPlanParallelTest extends AbstractExecutionPlanSpec {

    FileSystem fs = NativeServicesTestFixture.instance.get(FileSystem)

    DefaultExecutionPlan executionPlan

    def taskNodeFactory = new TaskNodeFactory(project.gradle, Stub(DocumentationRegistry), Stub(BuildTreeWorkGraphController), nodeValidator)

    def setup() {
        def dependencyResolver = new TaskDependencyResolver([new TaskNodeDependencyResolver(taskNodeFactory)])
        executionPlan = new DefaultExecutionPlan(Path.ROOT.toString(), taskNodeFactory, dependencyResolver, new ExecutionNodeAccessHierarchy(CASE_SENSITIVE, fs), new ExecutionNodeAccessHierarchy(CASE_SENSITIVE, fs), coordinator)
    }

    Node priorityNode(Map<String, ?> options = [:]) {
        return new TestPriorityNode(options.failure)
    }

    TaskInternal task(Map<String, ?> options = [:], String name) {
        def task = createTask(name, options.project ?: this.project, options.type ?: TaskInternal)
        _ * task.taskDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.lifecycleDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.finalizedBy >> taskDependencyResolvingTo(task, options.finalizedBy ?: [])
        _ * task.shouldRunAfter >> taskDependencyResolvingTo(task, options.shouldRunAfter ?: [])
        _ * task.mustRunAfter >> taskDependencyResolvingTo(task, options.mustRunAfter ?: [])
        _ * task.sharedResources >> (options.resources ?: [])
        TaskStateInternal state = Mock()
        _ * task.state >> state
        if (options.failure != null) {
            failure(task, options.failure)
        }
        return task
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
        assertTaskReady(finalizerDepDep)
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
        assertTaskReady(finalizerDep1)
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
        assertNextTaskReady(broken)
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

    def "finalizer dependency runs in parallel with finalized task when that dependency is also an entry point task"() {
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

    def "finalizer dependency runs in parallel with finalized task when that dependency is also a later entry point task"() {
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

    def "finalizer dependency runs even when finalizer does not run when dependency is also an entry point task"() {
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
        assertTaskReady(finalizerDep)
        assertAllWorkComplete(true)
    }

    def "finalizer and dependencies are executed even if the finalized task did not run when finalizer is also an entry point task"() {
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

    def "finalizer that is a dependency of multiple finalizers and an entry point task"() {
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
        assertTasksReady(dep, c, finalizerDep)
        assertTaskReady(finalizer)
        assertTaskReadyAndNoMoreToStart(a)
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
        assertNoWorkReadyToStartAfterSelect()
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
        assertNoWorkReadyToStartAfterSelect()

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
    @Requires(TestPrecondition.SYMLINKS)
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
    @Requires(TestPrecondition.SYMLINKS)
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
    @Requires(TestPrecondition.SYMLINKS)
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
        assertNoWorkReadyToStartAfterSelect()

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
        assertLastTaskOfGroupReady(producer)
        assertLastTaskOfGroupReadyAndNoMoreToStart(destroyer)
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
        assertLastTaskOfGroupReady(destroyer)
        assertLastTaskOfGroupReadyAndNoMoreToStart(producer)
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
        assertLastTasksOfGroupReadyAndNoMoreToStart(producer1, producer2)
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
        assertNoWorkReadyToStartAfterSelect()

        when:
        node1.execute()
        finishedExecuting(node1)
        def node2 = selectNextNode()
        def node3 = selectNextNode()

        then:
        node2.task == destroyer
        node3 instanceof OrdinalNode && node3.type == OrdinalNode.Type.DESTROYER
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node3)
        def node4 = selectNextNode()

        then:
        assertIsResolveMutationsOf(node4, producer)
        assertNoWorkReadyToStartAfterSelect()

        when:
        node4.execute()
        finishedExecuting(node4)
        def node5 = selectNextNode()

        then:
        node5 instanceof OrdinalNode && node5.type == OrdinalNode.Type.PRODUCER

        when:
        finishedExecuting(node5)

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
        assertNoWorkReadyToStartAfterSelect()

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
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(dependencyOfDependencyNode)
        def dependencyNode = selectNextTaskNode()
        then:
        dependencyNode.task == dependency
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(dependencyNode)
        def otherTaskWithDependencyNode = selectNextTaskNode()
        then:
        otherTaskWithDependencyNode.task == otherTaskWithDependency
        assertNoWorkReadyToStartAfterSelect()

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
        assertNoWorkReadyToStartAfterSelect()
        node1.task == dependency

        when:
        finishedExecuting(node1)
        def node2 = selectNextTaskNode()

        then:
        assertNoWorkReadyToStartAfterSelect()
        node2.task == finalized

        when:
        finishedExecuting(node2)
        def node3 = selectNextTaskNode()

        then:
        assertNoWorkReadyToStartAfterSelect()
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
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(finalizedNode)
        def node = selectNextNode()
        node.execute()
        finishedExecuting(node)

        then:
        assertAllWorkComplete(true)

        when:
        def failures = []
        coordinator.withStateLock {
            executionPlan.collectFailures(failures)
        }

        then:
        failures.size() == 1
        def e = failures.first()
        e.message.contains("Execution failed for task :finalizer")

        then:
        coordinator.withStateLock {
            executionPlan.getNode(finalized).isSuccessful()
            executionPlan.getNode(finalizer).state == Node.ExecutionState.FAILED_DEPENDENCY
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
            executionPlan.addEntryTasks([task])
        }
    }

    private void addToGraph(Node... nodes) {
        nodes.each {
            it.require()
            it.dependenciesProcessed()
        }
        executionPlan.addNodes(nodes as List)
    }

    private void addToGraphAndPopulate(Task... tasks) {
        addToGraph(tasks)
        populateGraph()
    }

    private void populateGraph() {
        executionPlan.determineExecutionPlan()
        executionPlan.finalizePlan()
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

    void assertNextTaskReady(Task task) {
        def node = selectNextTaskNode()
        assert node.task == task
        assertWorkReadyToStart()
        finishedExecuting(node)
    }

    void assertTaskReady(Task task, boolean needToSelect = true) {
        def node = selectNextTaskNode()
        assert node.task == task
        if (needToSelect) {
            assertNoWorkReadyToStartAfterSelect()
        } else {
            assertNoWorkReadyToStart()
        }
        finishedExecuting(node)
    }

    void assertLastTaskOfGroupReady(Task task, boolean needToSelect = true) {
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

    void assertLastTaskOfGroupReadyAndNoMoreToStart(Task task, boolean needToSelect = false) {
        def node = selectNextTaskNode()
        assert node.task == task
        def ordinalNode = selectNextNode()
        assert ordinalNode instanceof OrdinalNode
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node)
        assertNoMoreWorkToStartButNotAllComplete(false)
        finishedExecuting(ordinalNode)
    }

    void assertTasksReady(Task task1, Task task2, boolean needToSelect = true) {
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

    void assertLastTasksOfGroupReadyAndNoMoreToStart(Task task1, Task task2, boolean needToSelect = false) {
        def node1 = selectNextTaskNode()
        assert node1.task == task1
        def node2 = selectNextTaskNode()
        assert node2.task == task2
        def node3 = selectNextNode()
        assert node3 instanceof OrdinalNode
        assertNoWorkReadyToStartAfterSelect()
        finishedExecuting(node2)
        assertNoWorkReadyToStart()
        finishedExecuting(node1)
        assertNoWorkReadyToStart()
        finishedExecuting(node3)
        def node4 = selectNextNode()
        assert node4 instanceof OrdinalNode
        assertNoMoreWorkToStartButNotAllComplete(needToSelect)
        finishedExecuting(node4)
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
        assertNoWorkReadyToStartAfterSelect()
        finishedExecuting(node3)
        finishedExecuting(node2)
        finishedExecuting(node1)
    }

    void assertTasksReady(Task task1, Task task2, Task task3, Task task4) {
        def node1 = selectNextTaskNode()
        assert node1.task == task1
        def node2 = selectNextTaskNode()
        assert node2.task == task2
        def node3 = selectNextTaskNode()
        assert node3.task == task3
        def node4 = selectNextTaskNode()
        assert node4.task == task4
        assertNoWorkReadyToStartAfterSelect()
        finishedExecuting(node4)
        finishedExecuting(node3)
        finishedExecuting(node2)
        finishedExecuting(node1)
    }

    private void finishedExecuting(Node node) {
        coordinator.withStateLock {
            executionPlan.finishedExecuting(node, null)
        }
    }

    private List<Integer> getOrdinalGroups() {
        return executionPlan.tasks.collect { taskNodeFactory.getNode(it).group.asOrdinal()?.ordinal }
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
                executionPlan.finishedExecuting(node, null)
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
            assert !executionPlan.allExecutionComplete()
            assert executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            recordLocks {
                selection = executionPlan.selectNext()
            }
            assert !selection.noMoreWorkToStart && !selection.noWorkReadyToStart
            assert !executionPlan.allExecutionComplete()
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
            while (executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart) {
                def selection = executionPlan.selectNext()
                if (selection.noWorkReadyToStart) {
                    break
                }
                assert !selection.noMoreWorkToStart
                def node = selection.item
                assert !(node instanceof LocalTaskNode)
                if (node instanceof SelfExecutingNode) {
                    node.execute(null)
                }
                executionPlan.finishedExecuting(node, null)
            }
            assert executionPlan.executionState() == WorkSource.State.NoWorkReadyToStart
            assert executionPlan.selectNext().noWorkReadyToStart
            assert executionPlan.executionState() == WorkSource.State.NoWorkReadyToStart
        }
    }

    void assertNoMoreWorkToStartButNotAllComplete(boolean needToSelect = false) {
        coordinator.withStateLock {
            if (needToSelect) {
                assert executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            } else {
                assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            }
            assert executionPlan.selectNext().noMoreWorkToStart
            assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert !executionPlan.allExecutionComplete()
        }
    }

    void assertAllWorkComplete(boolean needToSelect = false) {
        coordinator.withStateLock {
            if (needToSelect) {
                assert executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            } else {
                assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            }
            assert executionPlan.selectNext().noMoreWorkToStart
            assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert executionPlan.allExecutionComplete()
        }
    }

    void assertNoWorkReadyToStart() {
        coordinator.withStateLock {
            assert executionPlan.executionState() == WorkSource.State.NoWorkReadyToStart
            assert executionPlan.selectNext().noWorkReadyToStart
            assert executionPlan.executionState() == WorkSource.State.NoWorkReadyToStart
        }
    }

    void assertNoWorkReadyToStartAfterSelect() {
        coordinator.withStateLock {
            // In some cases, a call to selectNext() is required to calculate that nothing is ready
            assert executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            assert executionPlan.selectNext().noWorkReadyToStart
            assert executionPlan.executionState() == WorkSource.State.NoWorkReadyToStart
        }
    }

    void assertWorkReadyToStart() {
        coordinator.withStateLock {
            assert executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
        }
    }

    private static class TestPriorityNode extends Node implements SelfExecutingNode {
        final Throwable failure

        TestPriorityNode(@Nullable Throwable failure) {
            this.failure = failure
        }

        @Override
        Throwable getNodeFailure() {
            return failure
        }

        @Override
        boolean isPriority() {
            return true
        }

        @Override
        void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        }

        @Override
        String toString() {
            return "test node"
        }

        @Override
        void execute(NodeExecutionContext context) {
        }
    }
}
