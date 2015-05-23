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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.internal.DefaultOperationDescriptor
import org.gradle.tooling.events.task.*
import org.gradle.tooling.model.gradle.BuildInvocations

class TaskProgressCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=1.0-milestone-8 <2.5")
    def "ignores listeners when Gradle version does not generate task events"() {
        given:
        goodCode()

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({
                    throw new RuntimeException()
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        noExceptionThrown()
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some task(s) to run first"
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations).forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    result << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).get()
        }

        then: "task progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    result << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then: "task progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "build aborts if a task listener throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then: "build aborts if the task listener throws an exception"
        thrown(GradleConnectionException)
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive current task progress event even if one of multiple task listeners throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        List<TaskProgressEvent> resultsOfFirstListener = new ArrayList<TaskProgressEvent>()
        List<TaskProgressEvent> resultsOfLastListener = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    resultsOfFirstListener << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).addProgressListener({ ProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }, EnumSet.of(OperationType.TASK)).addProgressListener({ ProgressEvent event ->
                    resultsOfLastListener << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then: "current task progress event must still be forwarded to the attached listeners even if one of the listeners throws an exception"
        thrown(GradleConnectionException)
        resultsOfFirstListener.size() >= 1
        resultsOfLastListener.size() >= 1
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for all tasks"() {
        given:
        buildFile << """
            apply plugin: 'java'
            compileJava.options.fork = true  // forked as 'Gradle Test Executor 1'
            classes.enabled = false

            task failingTask() << { throw new RuntimeException() }

        """

        file("src/main/java/example/MyClass.java") << """
            package example;
            public class MyClass {
                public void foo() throws Exception {
                    Thread.sleep(100);
                }
            }
        """

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('classes').addProgressListener({ ProgressEvent event ->
                    result << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 6              // compileJava, processResources, classes
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def compileJavaStartEvent = result[0]
        compileJavaStartEvent instanceof TaskStartEvent &&
            compileJavaStartEvent.eventTime > 0 &&
            compileJavaStartEvent.displayName == "Task :compileJava started" &&
            compileJavaStartEvent.descriptor.name == 'compileJava' &&
            compileJavaStartEvent.descriptor.displayName == 'Task :compileJava' &&
            compileJavaStartEvent.descriptor.taskPath == ':compileJava' &&
            compileJavaStartEvent.descriptor.parent == null
        def compileJavaFinishEvent = result[1]
        compileJavaFinishEvent instanceof TaskFinishEvent &&
            compileJavaFinishEvent.eventTime > 0 &&
            compileJavaFinishEvent.displayName == "Task :compileJava succeeded" &&
            compileJavaFinishEvent.descriptor.name == 'compileJava' &&
            compileJavaFinishEvent.descriptor.displayName == 'Task :compileJava' &&
            compileJavaFinishEvent.descriptor.taskPath == ':compileJava' &&
            compileJavaFinishEvent.descriptor.parent == null &&
            compileJavaFinishEvent.result instanceof TaskSuccessResult &&
            compileJavaFinishEvent.result.startTime == compileJavaStartEvent.eventTime &&
            compileJavaFinishEvent.result.endTime == compileJavaFinishEvent.eventTime &&
            !compileJavaFinishEvent.result.isUpToDate()
        def processResourcesStartEvent = result[2]
        processResourcesStartEvent instanceof TaskStartEvent &&
            processResourcesStartEvent.eventTime > 0 &&
            processResourcesStartEvent.displayName == "Task :processResources started" &&
            processResourcesStartEvent.descriptor.name == 'processResources' &&
            processResourcesStartEvent.descriptor.displayName == 'Task :processResources' &&
            processResourcesStartEvent.descriptor.taskPath == ':processResources' &&
            processResourcesStartEvent.descriptor.parent == null
        def processResourcesFinishEvent = result[3]
        processResourcesFinishEvent instanceof TaskFinishEvent &&
            processResourcesFinishEvent.eventTime > 0 &&
            processResourcesFinishEvent.displayName == "Task :processResources succeeded" &&
            processResourcesFinishEvent.descriptor.name == 'processResources' &&
            processResourcesFinishEvent.descriptor.displayName == 'Task :processResources' &&
            processResourcesFinishEvent.descriptor.taskPath == ':processResources' &&
            processResourcesFinishEvent.descriptor.parent == null &&
            processResourcesFinishEvent.result instanceof TaskSuccessResult &&
            processResourcesFinishEvent.result.startTime == processResourcesStartEvent.eventTime &&
            processResourcesFinishEvent.result.endTime == processResourcesFinishEvent.eventTime &&
            processResourcesFinishEvent.result.upToDate
        def classesStartEvent = result[4]
        classesStartEvent instanceof TaskStartEvent &&
            classesStartEvent.eventTime > 0 &&
            classesStartEvent.displayName == "Task :classes started" &&
            classesStartEvent.descriptor.name == 'classes' &&
            classesStartEvent.descriptor.displayName == 'Task :classes' &&
            classesStartEvent.descriptor.taskPath == ':classes' &&
            classesStartEvent.descriptor.parent == null
        def classesFinishEvent = result[5]
        classesFinishEvent instanceof TaskFinishEvent &&
            classesFinishEvent.eventTime > 0 &&
            classesFinishEvent.displayName == "Task :classes skipped" &&
            classesFinishEvent.descriptor.name == 'classes' &&
            classesFinishEvent.descriptor.displayName == 'Task :classes' &&
            classesFinishEvent.descriptor.taskPath == ':classes' &&
            classesFinishEvent.descriptor.parent == null &&
            classesFinishEvent.result instanceof TaskSkippedResult &&
            classesFinishEvent.result.startTime == classesStartEvent.eventTime &&
            classesFinishEvent.result.endTime == classesFinishEvent.eventTime &&
            classesFinishEvent.result.skipMessage == 'SKIPPED'
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for successful tasks"() {
        given:
        goodCode()

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    result << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        result.size() == 2 * tasks.size()
        assertOrderedEvents(result, tasks)

        where:
        tasks = [
            compileJava     : ['started', 'succeeded'],
            processResources: ['started', 'up-to-date'],
            classes         : ['started', 'succeeded'],
            jar             : ['started', 'succeeded'],
            assemble        : ['started', 'succeeded']
        ]
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for failed tasks"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
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
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener({ ProgressEvent event ->
                    result << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        BuildException ex = thrown()
        ex.cause.cause.message =~ /Execution failed for task ':test'/
        result.size() == 2 * tasks.size()
        assertOrderedEvents(result, tasks)

        where:
        tasks = [
            compileJava         : ['started', 'up-to-date'],
            processResources    : ['started', 'up-to-date'],
            classes             : ['started', 'up-to-date'],
            compileTestJava     : ['started', 'succeeded'],
            processTestResources: ['started', 'up-to-date'],
            testClasses         : ['started', 'succeeded'],
            test                : ['started', 'failed']
        ]
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for disabled tasks"() {
        buildFile << """
            apply plugin: 'java'
            compileJava.options.fork = true  // forked as 'Gradle Test Executor 1'
            assemble.enabled = false
        """

        file("src/main/java/example/MyClass.java") << """
            package example;
            public class MyClass {
                public void foo() throws Exception {
                    Thread.sleep(100);
                }
            }
        """

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    result << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        result.size() == 2 * tasks.size()
        assertOrderedEvents(result, tasks)

        where:
        tasks = [
            compileJava     : ['started', 'succeeded'],
            processResources: ['started', 'up-to-date'],
            classes         : ['started', 'succeeded'],
            jar             : ['started', 'succeeded'],
            assemble        : ['started', 'skipped']
        ]
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events when tasks are executed in parallel"() {
        given:
        buildFile << """
            @ParallelizableTask
            class ParTask extends DefaultTask {
                @TaskAction zzz() { Thread.sleep(1000) }
            }

            task para1(type:ParTask)
            task para2(type:ParTask)
            task parallelSleep(dependsOn:[para1,para2])
        """

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments("-Dorg.gradle.parallel.intra=true", '--parallel', '--max-workers=2').forTasks('parallelSleep').addProgressListener({ ProgressEvent event ->
                    result << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        result.size() == 2 * tasks.size()
        assertUnorderedEvents(result, tasks)

        where:
        tasks = [
            para1        : ['started', 'succeeded'],
            para2        : ['started', 'succeeded'],
            parallelSleep: ['started', 'succeeded']
        ]
    }

    @TargetGradleVersion('>=2.5')
    @ToolingApiVersion('>=2.5')
    @NotYetImplemented
    def "should receive task events from buildSrc"() {
        buildFile << """
            apply plugin: 'java'
            task dummy()
        """

        file("buildSrc/build.gradle") << """
            task taskInBuildSrc()
        """

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('dummy').addProgressListener({ ProgressEvent event ->
                result << (event as TaskProgressEvent)
            }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        result.size() == 2 * tasks.size()
        assertOrderedEvents(result, tasks)

        where:
        tasks = [
            ':buildSrc:clean'               : ['started', 'up-to-date'],
            ':buildSrc:compileJava'         : ['started', 'up-to-date'],
            ':buildSrc:compileGroovy'       : ['started', 'up-to-date'],
            ':buildSrc:processResources'    : ['started', 'up-to-date'],
            ':buildSrc:classes'             : ['started', 'up-to-date'],
            ':buildSrc:jar'                 : ['started', 'succeeded'],
            ':buildSrc:assemble'            : ['started', 'succeeded'],
            ':buildSrc:compileTestJava'     : ['started', 'up-to-date'],
            ':buildSrc:compileTestGroovy'   : ['started', 'up-to-date'],
            ':buildSrc:processTestResources': ['started', 'up-to-date'],
            ':buildSrc:testClasses'         : ['started', 'up-to-date'],
            ':buildSrc:test'                : ['started', 'up-to-date'],
            ':buildSrc:build'               : ['started', 'up-to-date'],
            ':dummy'                        : ['started', 'up-to-date']
        ]
    }

    @TargetGradleVersion('>=2.5')
    @ToolingApiVersion('>=2.5')
    @NotYetImplemented
    def "should receive task events from GradleBuild"() {
        buildFile << """
            task innerBuild(type:GradleBuild) {
                buildFile = file('other.gradle')
                tasks = ['innerTask']
            }
        """

        file("other.gradle") << """
            task innerTask()
        """

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('innerBuild').addProgressListener({ ProgressEvent event ->
                result << (event as TaskProgressEvent)
            }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        result.size() == 2 * tasks.size()
        assertUnorderedEvents(result, tasks)

        where:
        tasks = [
            ':innerTask' : ['started', 'up-to-date'],
            ':innerBuild': ['started', 'succeeded']
        ]
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "task operations have root build operation as parent iff build listener is attached"() {
        given:
        goodCode()

        when: 'listening to task progress events and build operation listener is attached'
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        if (event instanceof TaskProgressEvent) {
                            result << (event as TaskProgressEvent)
                        }
                    }
                }, EnumSet.of(OperationType.GENERIC, OperationType.TASK)).run()
        }

        then: 'the parent of the task events is the root build operation'
        !result.isEmpty()
        result.each { def event ->
            assert event.descriptor.parent instanceof DefaultOperationDescriptor
            assert event.descriptor.parent.parent instanceof DefaultOperationDescriptor
            assert event.descriptor.parent.parent.parent == null
        }

        when: 'listening to task progress events when no build operation listener is attached'
        result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--rerun-tasks').forTasks('assemble').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TaskProgressEvent)
                    }
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then: 'the parent of the task events is null'
        !result.isEmpty()
        result.each { def event ->
            assert event.descriptor.parent == null
        }
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

    private static void assertUnorderedEvents(List<TaskProgressEvent> events, Map<String, List<String>> tasks) {
        assertEvents(events, tasks, false)
    }

    private static void assertOrderedEvents(List<TaskProgressEvent> events, Map<String, List<String>> tasks) {
        assertEvents(events, tasks, true)
    }

    private static void assertEvents(List<TaskProgressEvent> events, Map<String, List<String>> tasks, boolean ordered) {
        int idx = 0
        long oldEndTime = 0
        if (!ordered) {
            // reorder events to make sure we can test that all events have their expected
            // outputs
            events = events.sort { it.descriptor.taskPath }
        }

        tasks.each { path, List<String> states ->
            states.each { state ->
                def event = events[idx]
                assert event.eventTime > 0
                if (ordered) {
                    assert event.eventTime >= oldEndTime
                }
                if (path.startsWith(':')) {
                    assert event.descriptor.taskPath == path
                } else {
                    assert event.descriptor.name == path
                }
                switch (state) {
                    case 'started':
                        assert event instanceof TaskStartEvent
                        break
                    case 'up-to-date':
                        assert event instanceof TaskFinishEvent
                        assert event.result instanceof TaskSuccessResult
                        if (ordered) {
                            assert event.result.startTime >= oldEndTime
                        }
                        assert event.result.endTime >= event.result.startTime
                        assert event.result.isUpToDate()
                        break
                    case 'skipped':
                        assert event instanceof TaskFinishEvent
                        assert event.result instanceof TaskSkippedResult
                        if (ordered) {
                            assert event.result.startTime >= oldEndTime
                        }
                        assert event.result.endTime >= event.result.startTime
                        assert event.result.skipMessage == 'SKIPPED'
                        break
                    case 'succeeded':
                        assert event instanceof TaskFinishEvent
                        assert event.result instanceof TaskSuccessResult
                        if (ordered) {
                            assert event.result.startTime >= oldEndTime
                        }
                        assert event.result.endTime >= event.result.startTime
                        break
                    case 'failed':
                        assert event instanceof TaskFinishEvent
                        assert event.result instanceof TaskFailureResult
                        if (ordered) {
                            assert event.result.startTime >= oldEndTime
                        }
                        assert event.result.endTime >= event.result.startTime
                        break
                    default:
                        throw new RuntimeException("Illegal state [$state]. Please check your test.")
                }
                oldEndTime = event.eventTime
                idx++
            }
        }
    }
}
