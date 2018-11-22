/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r51

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.work.WorkItemOperationDescriptor
import org.gradle.workers.fixtures.WorkerExecutorFixture

@ToolingApiVersion('>=5.1')
@TargetGradleVersion('>=4.0')
class WorkItemProgressEventCrossVersionTest extends ToolingApiSpecification {

    def fixture = new WorkerExecutorFixture(temporaryFolder)

    void setup() {
        fixture.prepareTaskTypeUsingWorker()
        fixture.withRunnableClassInBuildSrc()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                displayName = "Test Work"
            }
        """
    }

    @TargetGradleVersion('>=5.1')
    def "reports typed work item progress events as descendants of tasks"() {
        when:
        def events = runBuild("runInWorker", EnumSet.allOf(OperationType))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        with(taskOperation.descendant("Test Work")) {
            successful
            workItem
            descriptor.className == "org.gradle.test.TestRunnable"
        }
    }

    @TargetGradleVersion('<5.1')
    def "reports generic work item progress events as descendants of tasks"() {
        when:
        def events = runBuild("runInWorker", EnumSet.allOf(OperationType))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        with(taskOperation.descendant("Test Work")) {
            successful
            buildOperation
        }
    }

    @TargetGradleVersion('>=5.1')
    def "does not report work item progress events when WORK_ITEM operations are not requested"() {
        when:
        def events = runBuild("runInWorker", EnumSet.complementOf(EnumSet.of(OperationType.WORK_ITEM)))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        taskOperation.descendants { it.descriptor.displayName == "Test Work" }.empty
    }

    @TargetGradleVersion('>=5.1')
    def "does not report work item progress events when TASK operations are not requested"() {
        when:
        def events = runBuild("runInWorker", EnumSet.of(OperationType.WORK_ITEM))

        then:
        events.empty
    }

    @TargetGradleVersion('>=5.1')
    def "includes failure in progress event"() {
        given:
        buildFile << """
            ${fixture.getRunnableThatFails(IllegalStateException, "something went horribly wrong")}
            runInWorker {
                displayName = null
                runnableClass = RunnableThatFails
            }
        """

        when:
        def events = ProgressEvents.create()
        runBuild("runInWorker", events, EnumSet.of(OperationType.TASK, OperationType.WORK_ITEM))

        then:
        thrown(BuildException)
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        with(taskOperation.child("RunnableThatFails")) {
            !successful
            workItem
            descriptor instanceof WorkItemOperationDescriptor
            descriptor.className == "RunnableThatFails"
            failures.size() == 1
            with (failures[0]) {
                message == "something went horribly wrong"
                description.startsWith("java.lang.IllegalStateException: something went horribly wrong")
            }
        }
    }

    private ProgressEvents runBuild(String task, Set<OperationType> operationTypes) {
        ProgressEvents events = ProgressEvents.create()
        runBuild(task, events, operationTypes)
        events
    }

    private Object runBuild(String task, ProgressListener listener, Set<OperationType> operationTypes) {
        withConnection {
            newBuild()
                .forTasks(task)
                .addProgressListener(listener, operationTypes)
                .run()
        }
    }

}
