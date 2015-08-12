/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.TestUtil.createChildProject
import static org.gradle.util.TestUtil.createRootProject

class DefaultTaskExecutionPlanParallelTaskHandlingTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()
    FileSystem fs = NativeServicesTestFixture.instance.get(FileSystem)

    DefaultTaskExecutionPlan executionPlan = new DefaultTaskExecutionPlan(Stub(BuildCancellationToken), true)
    DefaultProject root = createRootProject()

    List<TaskInfo> startedTasks = []
    List<Thread> blockedThreads = []

    void cleanup() {
        completeAllStartedTasks()
        allBlockedThreadsFinish()
    }

    private void addToGraphAndPopulate(Task... tasks) {
        executionPlan.addToTaskGraph(Arrays.asList(tasks))
        executionPlan.determineExecutionPlan()
    }

    void startTasks(int num) {
        num.times { startedTasks << executionPlan.getTaskToExecute() }
    }

    void noMoreTasksCurrentlyAvailableForExecution() {
        blockedThreads << blockedThread { executionPlan.taskComplete(executionPlan.getTaskToExecute()) }
    }

    void completeAllStartedTasks() {
        startedTasks.each { executionPlan.taskComplete(it) }
        startedTasks.clear()
    }

    void allBlockedThreadsFinish() {
        blockedThreads*.join()
        blockedThreads.clear()
    }

    TestFile file(String path) {
        tmp.file(path)
    }

    @ParallelizableTask
    static class Parallel extends DefaultTask {}

    static class ParallelChild extends Parallel {}

    Thread blockedThread(Runnable target) {
        def thread = new Thread(target)

        thread.start()
        ConcurrentTestUtil.poll(3, 0.01) {
            assert thread.state == Thread.State.WAITING
        }
        thread
    }

    void requestedTasksBecomeAvailableForExecution() {
        allBlockedThreadsFinish()
    }

    def "tasks arent parallelized unless toggle is on"() {
        given:
        executionPlan = new DefaultTaskExecutionPlan(Stub(BuildCancellationToken), false)
        Task a = root.task("a")
        Task b = root.task("b")

        when:
        addToGraphAndPopulate(a, b)

        then:
        startTasks(1)

        and:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    def "two dependent parallelizable tasks are not executed in parallel"() {
        given:
        Task a = root.task("a", type: Parallel)
        Task b = root.task("b", type: Parallel).dependsOn(a)

        when:
        addToGraphAndPopulate(b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    def "two parallelizable tasks with must run after ordering are not executed in parallel"() {
        given:
        Task a = root.task("a", type: Parallel)
        Task b = root.task("b", type: Parallel).mustRunAfter(a)

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    def "task that extend a parallelizable task are not parallelizable by default"() {
        given:
        Task a = root.task("a", type: ParallelChild)
        Task b = root.task("b", type: ParallelChild)

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    def "task is not available for execution until all of its dependencies that are executed in parallel complete"() {
        given:
        Task a = root.task("a", type: Parallel)
        Task b = root.task("b", type: Parallel)
        Task c = root.task("c", type: Parallel).dependsOn(a, b)

        when:
        addToGraphAndPopulate(c)
        startTasks(2)

        then:
        noMoreTasksCurrentlyAvailableForExecution()

        when:
        completeAllStartedTasks()

        then:
        requestedTasksBecomeAvailableForExecution()
    }

    def "a parallelizable task with custom actions is not run in parallel"() {
        given:
        Task a = root.task("a", type: Parallel)
        Task b = root.task("b", type: Parallel).doLast {}

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    def "DefaultTask is parallelizable"() {
        given:
        Task a = root.task("a")
        Task b = root.task("b")

        when:
        addToGraphAndPopulate(a, b)

        then:
        startTasks(2)
    }

    def "Delete tasks are not parallelizable"() {
        given:
        Task clean = root.task("clean", type: Delete)
        Task parallelizable = root.task("parallelizable", type: Parallel)

        when:
        addToGraphAndPopulate(clean, parallelizable)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    @ParallelizableTask
    static class ParallelWithOutputFile extends DefaultTask {
        @OutputFile
        File outputFile
    }

    Task taskWithOutputFile(Project project = root, String taskName, File file) {
        project.task(taskName, type: ParallelWithOutputFile) {
            outputFile = file
        }
    }

    def "two parallelizable tasks that have the same file in outputs are not executed in parallel"() {
        given:
        Task a = taskWithOutputFile("a", file("output"))
        Task b = taskWithOutputFile("b", file("output"))

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    @ParallelizableTask
    static class ParallelWithOutputDirectory extends DefaultTask {
        @OutputDirectory
        File outputDirectory
    }

    Task taskWithOutputDirectory(Project project = root, String taskName, File directory) {
        project.task(taskName, type: ParallelWithOutputDirectory) {
            outputDirectory = directory
        }
    }

    def "a task that writes into a directory that is an output of currently running task is not started"() {
        given:
        Task a = taskWithOutputDirectory("a", file("outputDir"))
        Task b = taskWithOutputFile("b", file("outputDir").file("outputSubdir").file("output"))

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    def "a task that writes into an ancestor directory of a file that is an output of currently running task is not started"() {
        given:
        Task a = taskWithOutputFile("a", file("outputDir").file("outputSubdir").file("output"))
        Task b = taskWithOutputDirectory("b", file("outputDir"))

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "a task that writes into a symlink that overlaps with output of currently running task is not started"() {
        given:
        def taskOutput = file("outputDir").createDir()
        def symlink = file("symlink")
        fs.createSymbolicLink(symlink, taskOutput)

        and:
        Task a = taskWithOutputDirectory("a", taskOutput)
        Task b = taskWithOutputFile("b", symlink.file("fileUnderSymlink"))

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
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
        Task a = taskWithOutputDirectory("a", taskOutput)
        Task b = taskWithOutputDirectory("b", symlink)

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()

        cleanup:
        assert symlink.delete()
    }

    def "tasks from two different projects that have the same file in outputs are not executed in parallel"() {
        given:
        Task a = taskWithOutputFile(createChildProject(root, "a"), "a", file("output"))
        Task b = taskWithOutputFile(createChildProject(root, "b"), "b", file("output"))

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }

    def "a task from different project that writes into a directory that is an output of currently running task is not started"() {
        given:
        Task a = taskWithOutputDirectory(createChildProject(root, "a"), "a", file("outputDir"))
        Task b = taskWithOutputFile(createChildProject(root, "b"), "b", file("outputDir").file("outputSubdir").file("output"))

        when:
        addToGraphAndPopulate(a, b)
        startTasks(1)

        then:
        noMoreTasksCurrentlyAvailableForExecution()
    }
}
