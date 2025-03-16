/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildException
import org.gradle.tooling.ListenerFailedException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.GradleVersion
import org.junit.Rule

class TaskProgressCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def "receive task progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some task(s) to run first"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations).forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.TASK)).get()
        }

        then: "task progress events must be forwarded to the attached listeners"
        !events.tasks.empty
        events.operations == events.tasks
    }

    def "receive task progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then: "task progress events must be forwarded to the attached listeners"
        !events.tasks.empty
        events.operations == events.tasks
    }

    def "receive current task progress event even if one of multiple task listeners throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        List<TaskProgressEvent> resultsOfFirstListener = []
        List<TaskProgressEvent> resultsOfLastListener = []
        def failure = new IllegalStateException("Throwing an exception on purpose")
        withConnection {
            ProjectConnection connection ->
                def build = connection.newBuild()
                build.forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    resultsOfFirstListener << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).addProgressListener({ ProgressEvent event ->
                    throw failure
                }, EnumSet.of(OperationType.TASK)).addProgressListener({ ProgressEvent event ->
                    resultsOfLastListener << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK))
                build.run()
        }

        then: "listener exception is wrapped"
        ListenerFailedException ex = thrown()
        ex.message.startsWith("Could not execute build using")
        ex.causes == [failure]

        and: "expected events received"
        resultsOfFirstListener.size() == 1
        resultsOfLastListener.size() == 1

        and: "build execution is successful"
        assertHasBuildSuccessfulLogging()
    }

    def "receive task progress events for successful tasks"() {
        given:
        goodCode()
        buildFile << """
            task disabled {
                enabled = false
            }
            classes.dependsOn disabled
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('classes').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        // Some tasks; there may be others
        def compileJava = events.operation('Task :compileJava')
        compileJava.descriptor.name == ":compileJava"
        compileJava.descriptor.taskPath == ":compileJava"
        !compileJava.result.upToDate

        def processResources = events.operation('Task :processResources')
        processResources.descriptor.name == ":processResources"
        processResources.descriptor.taskPath == ":processResources"
        assertEmptyInputsTask(processResources)

        def disabled = events.operation('Task :disabled')
        disabled.descriptor.name == ":disabled"
        disabled.descriptor.taskPath == ":disabled"
        disabled.result instanceof TaskSkippedResult
        disabled.result.skipMessage == "SKIPPED"

        def classes = events.operation('Task :classes')
        classes.descriptor.name == ":classes"
        classes.descriptor.taskPath == ":classes"
        !classes.result.upToDate
    }

    def "receive task progress events for failed tasks"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/test/java/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     Thread.sleep(100);  // sleep for a moment to ensure test duration is > 0 (due to limited clock resolution)
                     throw new RuntimeException("broken", new RuntimeException("nope"));
                }
            }
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        BuildException ex = thrown()
        ex.cause.cause.message =~ /Execution failed for task ':test'/

        def test = events.operation("Task :test")
        test.failed
        test.failures.size() == 1
        test.failures[0].message == "Execution failed for task ':test'."

        events.failed == [test]
    }

    @TargetGradleVersion(">=3.0 <3.6")
    def "receive task progress events when tasks are executed in parallel"() {
        given:
        server.start()
        buildFile << """
            @ParallelizableTask
            class ParTask extends DefaultTask {
                @TaskAction zzz() { ${server.callFromBuildUsingExpression('name')} }
            }

            task para1(type:ParTask)
            task para2(type:ParTask)
            task parallelTasks(dependsOn:[para1,para2])
        """
        server.expectConcurrent("para1", "para2")

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments("-Dorg.gradle.parallel.intra=true", '--parallel', '--max-workers=2').forTasks('parallelTasks').addProgressListener(events).run()
        }

        then:
        events.tasks.size() == 3

        def runTasks = events.operation("Run tasks")

        def t1 = events.operation("Task :para1")
        def t2 = events.operation("Task :para2")
        def t3 = events.operation("Task :parallelTasks")

        t1.parent == runTasks
        t2.parent == runTasks
        t3.parent == runTasks

        cleanup:
        server.stop()
    }

    @TargetGradleVersion(">=3.6")
    def "receive task progress events when tasks are executed in parallel (with async work)"() {
        given:
        server.start()
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            ${targetVersion < GradleVersion.version("6.0") ? defineGradle3WorkerRunnable() : defineGradleWorkAction()}

            task para1(type:ParTask)
            task para2(type:ParTask)
            task parallelTasks(dependsOn:[para1,para2])
        """
        server.expectConcurrent("para1", "para2")

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--parallel', '--max-workers=2').forTasks('parallelTasks').addProgressListener(events).run()
        }

        then:
        events.tasks.size() == 3

        def runTasks = events.operation("Run tasks")

        def t1 = events.operation("Task :para1")
        def t2 = events.operation("Task :para2")
        def t3 = events.operation("Task :parallelTasks")

        t1.parent == runTasks
        t2.parent == runTasks
        t3.parent == runTasks

        cleanup:
        server.stop()
    }

    private defineGradle3WorkerRunnable() {
        """
        class TestRunnable implements Runnable {
            String name

            @Inject
            public TestRunnable(String name) { this.name = name }

            public void run() {
                ${server.callFromBuildUsingExpression('name')}
            }
        }

        class ParTask extends DefaultTask {
            @TaskAction zzz() {
                services.get(WorkerExecutor.class).submit(TestRunnable) {
                    it.isolationMode = org.gradle.workers.IsolationMode.NONE
                    it.params = [ name ]
                }
            }
        }
        """
    }

    private defineGradleWorkAction() {
        """
        interface WorkActionParams extends WorkParameters {
            Property<String> getName()
        }
        abstract class TestWorkAction implements WorkAction<WorkActionParams> {
            public void execute() {
                ${server.callFromBuildUsingExpression('parameters.name.get()')}
            }
        }

        class ParTask extends DefaultTask {
            @TaskAction zzz() {
                def taskName = name
                services.get(WorkerExecutor.class).noIsolation().submit(TestWorkAction) {
                    it.name = taskName
                }
            }
        }
        """
    }

    @ToolingApiVersion("<8.12")
    def "task operations have a build operation as parent iff build listener is attached  (Tooling API client < 8.12)"() {
        given:
        goodCode()

        when: 'listening to task progress events and build operation listener is attached'
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.GENERIC, OperationType.TASK)).run()
        }

        then: 'the parent of the task events is the root build operation'
        def runTasks = events.operation("Run tasks")
        events.tasks.every { it.descriptor.parent == runTasks.descriptor }

        when: 'listening to task progress events when no build operation listener is attached'
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--rerun-tasks').forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then: 'the parent of the task events is null'
        events.tasks.every { it.descriptor.parent == null }
    }

    @ToolingApiVersion(">=8.12")
    def "task operations have a build operation as parent iff build listener is attached (Tooling API client >= 8.12)"() {
        given:
        goodCode()

        when: 'listening to task progress events and build operation listener is attached'
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.GENERIC, OperationType.TASK, OperationType.ROOT)).run()
        }

        then: 'the parent of the task events is the root build operation'
        def runTasks = events.operation("Run tasks")
        events.tasks.every { it.descriptor.parent == runTasks.descriptor }

        when: 'listening to task progress events when no build operation listener is attached'
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--rerun-tasks').forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then: 'the parent of the task events is null'
        events.tasks.every { it.descriptor.parent == null }
    }

    @TargetGradleVersion(">=3.4")
    def "task with empty skipwhenempty inputs marked as skipped with NO-SOURCE"() {
        given:
        buildFile << """
           task empty {
                inputs.files(project.files()).skipWhenEmpty()
                doLast{}
           }
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('empty').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then:

        def emptyTask = events.operation('Task :empty')
        emptyTask.descriptor.name == ":empty"
        emptyTask.descriptor.taskPath == ":empty"
        emptyTask.result instanceof TaskSkippedResult
        emptyTask.result.skipMessage == "NO-SOURCE"
    }

    def assertEmptyInputsTask(ProgressEvents.Operation taskOperation) {
        if (targetVersion < GradleVersion.version("3.4")) {
            assert taskOperation.result.upToDate
        } else {
            assert taskOperation.result instanceof TaskSkippedResult
            assert taskOperation.result.skipMessage == "NO-SOURCE"
        }
        true
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            compileJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/main/java/example/MyClass.java") << """
            package example;
            public class MyClass {
                public void foo() throws Exception {
                    Thread.sleep(100);
                }
            }
        """
    }
}
