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
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule

import java.util.concurrent.BlockingQueue

import static org.gradle.util.TestUtil.createChildProject
import static org.gradle.util.TestUtil.createRootProject

@CleanupTestDirectory
@UsesNativeServices
class DefaultTaskExecutionPlanConcurrentTest extends ConcurrentSpec {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()
    DefaultTaskExecutionPlan executionPlan
    ProjectInternal root
    def cancellationHandler = Mock(BuildCancellationToken)
    def listenerManager = new DefaultListenerManager()
    def projectLockService = new DefaultProjectLockService(listenerManager, true)

    def setup() {
        root = createRootProject(temporaryFolder.testDirectory)
        executionPlan = new DefaultTaskExecutionPlan(cancellationHandler, projectLockService)
    }

    def "one non parallelizable parallel task per project is allowed"() {
        given:
        //2 projects, 2 non parallelizable tasks each
        def projectA = createChildProject(root, "a")
        def projectB = createChildProject(root, "b")

        def fooA = projectA.task("foo").doLast {}
        def barA = projectA.task("bar").doLast {}

        def fooB = projectB.task("foo").doLast {}
        def barB = projectB.task("bar").doLast {}

        addToGraphAndPopulate([fooA, barA, fooB, barB])

        TaskInfo task1
        TaskInfo task2
        TaskInfo task3
        TaskInfo task4

        when:
        async {
            def taskWorker1 = taskWorker()
            def taskWorker2 = taskWorker()

            task1 = taskWorker1.take()
            task2 = taskWorker2.take()

            instant."complete${task1.task.path}"
            instant."complete${task2.task.path}"

            task3 = taskWorker1.take()
            task4 = taskWorker2.take()

            instant."complete${task3.task.path}"
            instant."complete${task4.task.path}"
        }

        then:
        task1.task.project != task2.task.project
        task3.task.project != task4.task.project
    }

    def "tasks arent parallelized if toggle is off"() {
        given:
        executionPlan = new DefaultTaskExecutionPlan(Stub(BuildCancellationToken), projectLockService, false)
        Task a = root.task("a", type: Parallel)
        Task b = root.task("b", type: Parallel)

        when:
        addToGraphAndPopulate([a, b])
        async {
            taskWorker()
            taskWorker()

            instant."complete${a.path}"
            instant."complete${b.path}"
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    def "task that extend a parallelizable task are not parallelizable by default"() {
        given:
        Task a = root.task("a", type: ParallelChild)
        Task b = root.task("b", type: ParallelChild)

        when:
        addToGraphAndPopulate([a, b])
        async {
            taskWorker()
            taskWorker()

            instant."complete${a.path}"
            instant."complete${b.path}"
        }

        then:
        operation."${b.path}".start > operation."${a.path}".end
    }

    private void addToGraphAndPopulate(List tasks) {
        executionPlan.addToTaskGraph(tasks)
        executionPlan.determineExecutionPlan()
    }

    BlockingQueue<TaskInfo> taskWorker() {
        def tasks = Queues.newLinkedBlockingQueue()
        start {
            def moreTasks = true
            while(moreTasks) {
                moreTasks = executionPlan.withTaskToExecute(new Action<TaskInfo>() {
                    @Override
                    void execute(TaskInfo taskInfo) {
                        operation."${taskInfo.task.path}" {
                            tasks.add(taskInfo)
                            thread.blockUntil."complete${taskInfo.task.path}"
                            executionPlan.taskComplete(taskInfo)
                        }
                    }
                })
            }
        }
        return tasks
    }

    @ParallelizableTask
    static class Parallel extends DefaultTask {}

    static class ParallelChild extends Parallel {}
}
