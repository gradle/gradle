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

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class DefaultExecutionPlanParallelTest extends AbstractExecutionPlanSpec {

    FileSystem fs = NativeServicesTestFixture.instance.get(FileSystem)
    int ordinal = 0

    DefaultExecutionPlan executionPlan

    def setup() {
        def taskNodeFactory = new TaskNodeFactory(project.gradle, Stub(DocumentationRegistry), Stub(BuildTreeWorkGraphController), nodeValidator)
        def dependencyResolver = new TaskDependencyResolver([new TaskNodeDependencyResolver(taskNodeFactory)])
        executionPlan = new DefaultExecutionPlan(Path.ROOT.toString(), taskNodeFactory, dependencyResolver, new ExecutionNodeAccessHierarchy(CASE_SENSITIVE, fs), new ExecutionNodeAccessHierarchy(CASE_SENSITIVE, fs), coordinator)
    }

    TaskInternal task(Map<String, ?> options = [:], String name) {
        def task = createTask(name, options.project ?: this.project, options.type ?: TaskInternal)
        _ * task.taskDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.lifecycleDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.finalizedBy >> taskDependencyResolvingTo(task, options.finalizedBy ?: [])
        _ * task.shouldRunAfter >> taskDependencyResolvingTo(task, [])
        _ * task.mustRunAfter >> taskDependencyResolvingTo(task, options.mustRunAfter ?: [])
        _ * task.sharedResources >> (options.resources ?: [])
        TaskStateInternal state = Mock()
        _ * task.state >> state
        if (options.failure != null) {
            failure(task, options.failure)
        }
        return task
    }

    def "does not attempt to run finalizer of task whose dependencies have failed"() {
        given:
        Task broken = task("a", failure: new RuntimeException())
        Task finalizerDep = task("b")
        Task finalizer = task("c", dependsOn: [finalizerDep])
        Task task = task("d", type: Async, dependsOn: [broken], finalizedBy: [finalizer])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(task)
        def node1 = selectNextTaskNode()

        then:
        node1.task == broken
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node1)

        then:
        if (continueOnFailure) {
            assertAllWorkCompleteAfterSelect()
        } else {
            assertAllWorkComplete()
        }

        where:
        continueOnFailure << [false, true]
    }

    def "finalizer tasks are executed on task failure but dependents of failed task are not"() {
        Task finalizerDep = task("a")
        Task finalizer = task("b", dependsOn: [finalizerDep])
        Task broken = task("c", finalizedBy: [finalizer], failure: new RuntimeException())
        Task task = task("d", dependsOn: [broken])

        when:
        executionPlan.setContinueOnFailure(continueOnFailure)
        addToGraphAndPopulate(task)

        def node1 = selectNextTaskNode()

        then:
        node1.task == broken
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node1)
        def node2 = selectNextTaskNode()

        then:
        node2.task == finalizerDep
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node2)
        def node3 = selectNextTaskNode()

        then:
        node3.task == finalizer
        assertNoMoreWorkToStartButNotAllComplete()

        when:
        finishedExecuting(node3)

        then:
        assertAllWorkComplete()

        where:
        continueOnFailure << [false, true]
    }

    def "task and finalizer are not executed when unrelated finalized task fails"() {
        Task finalizer1 = task("a")
        Task broken = task("b", finalizedBy: [finalizer1], failure: new RuntimeException())
        Task finalizer2 = task("c")
        Task unrelated = task("d", finalizedBy: [finalizer2])

        when:
        addToGraphAndPopulate(broken, unrelated)

        def node1 = selectNextTaskNode()

        then:
        node1.task == broken
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node1)
        def node2 = selectNextTaskNode()

        then:
        node2.task == finalizer1
        assertNoMoreWorkToStartButNotAllComplete()

        when:
        finishedExecuting(node2)

        then:
        assertAllWorkComplete()
    }

    def "task and finalizer are executed when unrelated finalized task fails and continue on failure"() {
        Task finalizer1 = task("a")
        Task broken = task("b", finalizedBy: [finalizer1], failure: new RuntimeException())
        Task finalizer2 = task("c")
        Task unrelated = task("d", finalizedBy: [finalizer2])

        when:
        executionPlan.setContinueOnFailure(true)
        addToGraphAndPopulate(broken, unrelated)

        def node1 = selectNextTaskNode()

        then:
        node1.task == broken
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node1)
        def node2 = selectNextTaskNode()

        then:
        node2.task == finalizer1
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node2)
        def node3 = selectNextTaskNode()

        then:
        node3.task == unrelated
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(node3)
        def node4 = selectNextTaskNode()

        then:
        node4.task == finalizer2
        assertNoMoreWorkToStartButNotAllComplete()

        when:
        finishedExecuting(node4)

        then:
        assertAllWorkComplete()
    }

    def "multiple tasks with async work from the same project can run in parallel"() {
        given:
        def foo = task("foo", type: Async)
        def bar = task("bar", type: Async)
        def baz = task("baz", type: Async)

        when:
        addToGraphAndPopulate(foo, bar, baz)

        def executedTasks = [selectNextTask(), selectNextTask(), selectNextTask()] as Set
        then:
        executedTasks == [bar, baz, foo] as Set
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
        addToGraphAndPopulate(foo, bar)
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

    def "finalizer runs after the last task to be finalized"() {
        given:
        def projectA = project(project, "a")
        Task finalizer = task("finalizer", project: projectA)
        Task a = task("a", project: projectA, type: Async, finalizedBy: [finalizer])
        def projectB = project(project, "b")
        Task b = task("b", project: projectB, type: Async, finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate(a, b)
        def firstInfo = selectNextTaskNode()
        def secondInfo = selectNextTaskNode()

        then:
        firstInfo.task == a
        secondInfo.task == b
        assertNoWorkReadyToStartAfterSelect()

        when:
        finishedExecuting(firstInfo)

        then:
        assertNoWorkReadyToStart()

        when:
        finishedExecuting(secondInfo)
        def finalizerInfo = selectNextTaskNode()

        then:
        finalizerInfo.task == finalizer
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
        assertAllWorkCompleteAfterSelect()

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
        addToGraphAndPopulate(second, first)
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
        def first = task("fist", type: Async)
        def second = task("second", type: Async)

        when:
        addToGraphAndPopulate(second, first)
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
        executionPlan.addEntryTasks(Arrays.asList(tasks), ordinal++)
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

    private void finishedExecuting(Node node) {
        coordinator.withStateLock {
            executionPlan.finishedExecuting(node, null)
        }
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
            assert executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
            recordLocks {
                selection = executionPlan.selectNext()
            }
            assert !selection.noMoreWorkToStart && !selection.noWorkReadyToStart
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

    void assertNoMoreWorkToStartButNotAllComplete() {
        coordinator.withStateLock {
            assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert executionPlan.selectNext().noMoreWorkToStart
            assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert !executionPlan.allExecutionComplete()
        }
    }

    void assertAllWorkComplete() {
        coordinator.withStateLock {
            assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert executionPlan.selectNext().noMoreWorkToStart
            assert executionPlan.executionState() == WorkSource.State.NoMoreWorkToStart
            assert executionPlan.allExecutionComplete()
        }
    }

    void assertAllWorkCompleteAfterSelect() {
        coordinator.withStateLock {
            assert executionPlan.executionState() == WorkSource.State.MaybeWorkReadyToStart
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
}
