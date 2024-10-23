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
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.util.GradleVersion

@TargetGradleVersion('>=5.1')
class WorkItemProgressEventCrossVersionSpec extends ToolingApiSpecification {

    def workActionClass = file("buildSrc/src/main/java/org/gradle/test/TestWork.java")

    def setup() {
        prepareTaskTypeUsingWorker()
        withRunnableClassInBuildSrc()
        buildFile << """
            task runInWorker(type: WorkerTask)
        """
    }

    def "reports typed work item progress events as descendants of tasks"() {
        when:
        def events = runBuild("runInWorker", EnumSet.allOf(OperationType))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.assertIsTask()
        with(taskOperation.descendant("org.gradle.test.TestWork")) {
            successful
            assertIsWorkItem()
            descriptor.className == "org.gradle.test.TestWork"
        }
    }

    @TargetGradleVersion('>=4.8 <5.1') // fixture uses ProjectLayout.files()
    def "reports generic work item progress events as descendants of tasks"() {
        when:
        def events = runBuild("runInWorker", EnumSet.allOf(OperationType))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.assertIsTask()
        with(taskOperation.descendant("org.gradle.test.TestWork")) {
            successful
            buildOperation
        }
    }

    def "does not report work item progress events when WORK_ITEM operations are not requested"() {
        when:
        def events = runBuild("runInWorker", EnumSet.complementOf(EnumSet.of(OperationType.WORK_ITEM)))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.assertIsTask()
        taskOperation.descendants { it.descriptor.displayName == "TestWork" }.empty
    }

    def "does not report work item progress events when TASK operations are not requested"() {
        when:
        def events = runBuild("runInWorker", EnumSet.of(OperationType.WORK_ITEM))

        then:
        events.empty
    }

    def "includes failure in progress event"() {
        given:
        withRunnableClassThatFails()

        when:
        def events = ProgressEvents.create()
        runBuild("runInWorker", events, EnumSet.of(OperationType.TASK, OperationType.WORK_ITEM))

        then:
        thrown(BuildException)
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.assertIsTask()
        with(taskOperation.child("org.gradle.test.TestWork")) {
            !successful
            assertIsWorkItem()
            descriptor.className == "org.gradle.test.TestWork"
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

    def prepareTaskTypeUsingWorker() {
        def gradle3Submit = 'workerExecutor.submit(TestWork) { c -> c.displayName = "org.gradle.test.TestWork" }'
        def submit = 'workerExecutor.noIsolation().submit(TestWork) { }'
        buildFile << """
            import javax.inject.Inject
            import org.gradle.workers.*

            class WorkerTask extends DefaultTask {

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void executeTask() {
                    ${targetVersion < GradleVersion.version("6.0") ? gradle3Submit : submit}
                }
            }
        """
    }

    void withRunnableClassInBuildSrc() {
        def gradle3Runnable = """
            public class TestWork implements Runnable {
                public void run() {
                    // DO NOTHING
                }
            }
        """
        def workAction = """
            abstract public class TestWork implements WorkAction<WorkParameters.None> {
                public void execute() {
                    // DO NOTHING
                }
            }
        """

        workActionClass << """
            package org.gradle.test;
            import org.gradle.workers.*;
            ${targetVersion < GradleVersion.version("6.0") ? gradle3Runnable : workAction}
        """
        buildFile.text = """
            import org.gradle.test.TestWork
            ${buildFile.text}
        """
    }

    void withRunnableClassThatFails() {
        workActionClass.text = workActionClass.text.replace('// DO NOTHING', 'throw new IllegalStateException("something went horribly wrong");')
    }
}
