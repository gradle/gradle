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

package org.gradle.execution.taskgraph

import com.google.common.collect.Queues
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.junit.Rule

import java.util.concurrent.BlockingQueue

import static org.gradle.util.TestUtil.createChildProject
import static org.gradle.util.TestUtil.createRootProject

@CleanupTestDirectory
@UsesNativeServices
class DefaultTaskExecutionPlanParallelTest extends ConcurrentSpec {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    FileSystem fs = NativeServicesTestFixture.instance.get(FileSystem)

    DefaultTaskExecutionPlan executionPlan
    ProjectInternal root
    def cancellationHandler = Mock(BuildCancellationToken)
    def coordinationService = new DefaultResourceLockCoordinationService()
    def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, true, 3)
    def parentWorkerLease = workerLeaseService.workerLease

    def setup() {
        root = createRootProject(temporaryFolder.testDirectory)
        executionPlan = new DefaultTaskExecutionPlan(cancellationHandler, coordinationService, workerLeaseService)
        parentWorkerLease.start()
    }

    def "multiple tasks with async work from the same project can run in parallel"() {
        given:
        def foo = root.task("foo", type: Async)
        def bar = root.task("bar", type: Async)
        def baz = root.task("baz", type: Async)

        expect:
        addToGraphAndPopulate(foo, bar, baz)
        async {
            populateReadyQueue()
            def taskWorker1 = taskWorker()
            def taskWorker2 = taskWorker()
            def taskWorker3 = taskWorker()

            def task1 = taskWorker1.take()
            def task2 = taskWorker2.take()
            def task3 = taskWorker3.take()

            releaseTasks(task1.task, task2.task, task3.task)
        }
    }

    def "one non-async task per project is allowed"() {
        given:
        //2 projects, 2 non parallelizable tasks each
        def projectA = createChildProject(root, "a")
        def projectB = createChildProject(root, "b")

        def fooA = projectA.task("foo").doLast {}
        def barA = projectA.task("bar").doLast {}

        def fooB = projectB.task("foo").doLast {}
        def barB = projectB.task("bar").doLast {}

        TaskInfo task1
        TaskInfo task2
        TaskInfo task3
        TaskInfo task4

        when:
        addToGraphAndPopulate(fooA, barA, fooB, barB)
        async {
            populateReadyQueue()
            def taskWorker1 = taskWorker()
            def taskWorker2 = taskWorker()

            task1 = taskWorker1.take()
            task2 = taskWorker2.take()

            releaseTasks(task1.task, task2.task)

            task3 = taskWorker1.take()
            task4 = taskWorker2.take()

            releaseTasks(task3.task, task4.task)
        }

        then:
        task1.task.project != task2.task.project
        task3.task.project != task4.task.project
    }

    def "a non-async task can start while an async task from the same project is waiting for work to complete"() {
        given:
        def bar = root.task("bar", type: Async)
        def foo = root.task("foo")

        expect:
        addToGraphAndPopulate(foo, bar)
        async {
            populateReadyQueue()
            def taskWorker1 = taskWorker()
            def taskWorker2 = taskWorker()

            def task1 = taskWorker1.take()
            def task2 = taskWorker2.take()

            releaseTasks(task1.task, task2.task)
        }
    }

    def "an async task does not start while a non-async task from the same project is running"() {
        given:
        def a = root.task("a")
        def b = root.task("b", type: Async)

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    def "two dependent tasks are not executed in parallel"() {
        given:
        Task a = root.task("a", type: Async)
        Task b = root.task("b", type: Async).dependsOn(a)

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    def "two tasks with must run after ordering are not executed in parallel"() {
        given:
        Task a = root.task("a", type: Async)
        Task b = root.task("b", type: Async).mustRunAfter(a)

        when:
        addToGraphAndPopulate(a,b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    def "two tasks with should run after ordering are executed in parallel" () {
        given:
        def a = root.task("a", type: Async)
        def b = root.task("b", type: Async)
        b.shouldRunAfter(a)

        expect:
        addToGraphAndPopulate(a, b)
        async {
            populateReadyQueue()
            def taskWorker1 = taskWorker()
            def taskWorker2 = taskWorker()

            def task1 = taskWorker1.take()
            def task2 = taskWorker2.take()

            releaseTasks(task1.task, task2.task)
        }
    }

    def "task is not available for execution until all of its dependencies that are executed in parallel complete"() {
        given:
        Task a = root.task("a", type: Async)
        Task b = root.task("b", type: Async)
        Task c = root.task("c", type: Async).dependsOn(a, b)

        when:
        addToGraphAndPopulate(a,b,c)
        async {
            startTaskWorkers(3)

            releaseTasks(a, b, c)
        }

        then:
        operation."${c.path}".start > operation."${a.path}".end
        operation."${c.path}".start > operation."${b.path}".end
    }

    def "two tasks that have the same file in outputs are not executed in parallel"() {
        def sharedFile = file("output")

        given:
        Task a = root.task("a", type: AsyncWithOutputFile) {
            outputFile = sharedFile
        }
        Task b = root.task("b", type: AsyncWithOutputFile) {
            outputFile = sharedFile
        }

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    def "a task that writes into a directory that is an output of a running task is not started"() {
        given:
        Task a = root.task("a", type: AsyncWithOutputDirectory) {
            outputDirectory = file("outputDir")
        }
        Task b = root.task("b", type: AsyncWithOutputDirectory) {
            outputDirectory = file("outputDir").file("outputSubdir").file("output")
        }

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    def "a task that writes into an ancestor directory of a file that is an output of a running task is not started"() {
        given:
        Task a = root.task("a", type: AsyncWithOutputDirectory) {
            outputDirectory = file("outputDir").file("outputSubdir").file("output")
        }
        Task b = root.task("b", type: AsyncWithOutputDirectory) {
            outputDirectory = file("outputDir")
        }

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "a task that writes into a symlink that overlaps with output of currently running task is not started"() {
        given:
        def taskOutput = file("outputDir").createDir()
        def symlink = file("symlink")
        fs.createSymbolicLink(symlink, taskOutput)

        and:
        Task a = root.task("a", type: AsyncWithOutputDirectory) {
            outputDirectory = taskOutput
        }
        Task b = root.task("b", type: AsyncWithOutputFile) {
            outputFile = symlink.file("fileUnderSymlink")
        }

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "a task that writes into a symlink of a shared output dir of currently running task is not started"() {
        given:
        def taskOutput = file("outputDir").createDir()
        def symlink = file("symlink")
        fs.createSymbolicLink(symlink, taskOutput)

        // Deleting any file clears the internal canonicalisation cache.
        // This allows the created symlink to be actually resolved.
        // See java.io.UnixFileSystem#cache.
        file("tmp").createFile().delete()

        and:
        Task a = root.task("a", type: AsyncWithOutputDirectory) {
            outputDirectory = taskOutput
        }
        Task b = root.task("b", type: AsyncWithOutputDirectory) {
            outputDirectory = symlink
        }

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end

        cleanup:
        assert symlink.delete()
    }

    def "tasks from two different projects that have the same file in outputs are not executed in parallel"() {
        given:
        Task a = createChildProject(root, "a").task("a", type: AsyncWithOutputFile) {
            outputFile = file("output")
        }
        Task b = createChildProject(root, "b").task("b", type: AsyncWithOutputFile) {
            outputFile = file("output")
        }

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    def "a task from different project that writes into a directory that is an output of currently running task is not started"() {
        given:
        Task a = createChildProject(root, "a").task("a", type: AsyncWithOutputDirectory) {
            outputDirectory = file("outputDir")
        }
        Task b = createChildProject(root, "b").task("b", type: AsyncWithOutputFile) {
            outputFile = file("outputDir").file("outputSubdir").file("output")
        }

        when:
        addToGraphAndPopulate(a, b)
        async {
            startTaskWorkers(2)

            releaseTasks(a, b)
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    private void addToGraphAndPopulate(Task... tasks) {
        executionPlan.addToTaskGraph(Arrays.asList(tasks))
        executionPlan.determineExecutionPlan()
    }

    void startTaskWorkers(int count) {
        populateReadyQueue()
        count.times {
            taskWorker()
        }
    }

    void releaseTasks(Task... tasks) {
        tasks.each { Task task ->
            instant."complete${task.path}"
        }
    }

    void populateReadyQueue() {
        start {
            executionPlan.populateReadyTaskQueue()
        }
    }

    BlockingQueue<TaskInfo> taskWorker() {
        def tasks = Queues.newLinkedBlockingQueue()
        start {
            def moreTasks = true
            while(moreTasks) {
                moreTasks = executionPlan.executeWithTask(parentWorkerLease, new Action<TaskInfo>() {
                    @Override
                    void execute(TaskInfo taskInfo) {
                        operation."${taskInfo.task.path}" {
                            tasks.add(taskInfo)
                            if (taskInfo.task instanceof Async) {
                                workerLeaseService.withoutProjectLock {
                                    thread.blockUntil."complete${taskInfo.task.path}"
                                }
                            } else {
                                thread.blockUntil."complete${taskInfo.task.path}"
                            }
                            executionPlan.taskComplete(taskInfo)
                        }
                    }
                })
            }
        }
        return tasks
    }

    TestFile file(String path) {
        temporaryFolder.file(path)
    }

    static class Async extends DefaultTask {}

    static class AsyncWithOutputFile extends Async {
        @OutputFile
        File outputFile
    }

    static class AsyncWithOutputDirectory extends DefaultTask {
        @OutputDirectory
        File outputDirectory
    }
}
