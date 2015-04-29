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
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
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
                connection.newBuild().forTasks('test').addTaskProgressListener { throw new RuntimeException() }.run()
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
                connection.model(BuildInvocations).forTasks('test').addTaskProgressListener { TaskProgressEvent event ->
                    result << event
                }.get()
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
                connection.newBuild().forTasks('test').addTaskProgressListener { TaskProgressEvent event ->
                    result << event
                }.run()
        }

        then: "test progress events must be forwarded to the attached listeners"
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
                connection.newBuild().forTasks('test').addTaskProgressListener { TaskProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }.run()
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
                connection.newBuild().forTasks('test').addTaskProgressListener { TaskProgressEvent event ->
                    resultsOfFirstListener.add(event)
                }.addTaskProgressListener { TaskProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }.addTaskProgressListener { TaskProgressEvent event ->
                    resultsOfLastListener.add(event)
                }.run()
        }

        then: "current task progress event must still be forwarded to the attached listeners even if one of the listeners throws an exception"
        thrown(GradleConnectionException)
        resultsOfFirstListener.size() == 1
        resultsOfLastListener.size() == 1
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for successful test run"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     Thread.sleep(100);  // sleep for a moment to ensure test duration is > 0 (due to limited clock resolution)
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTaskProgressListener { TaskProgressEvent event ->
                    assert event != null
                    result << event
                }.run()
        }

        then:
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
                test                : ['started', 'succeeded']
        ]
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for failed test run"() {
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
                connection.newBuild().forTasks('test').addTaskProgressListener { TaskProgressEvent event ->
                    assert event != null
                    result << event
                }.run()
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
    def "receive task progress events for disabled test run"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
            test.enabled = false
        """

        file("src/test/java/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     Thread.sleep(100);  // sleep for a moment to ensure test duration is > 0 (due to limited clock resolution)
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when:
        List<TaskProgressEvent> result = new ArrayList<TaskProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTaskProgressListener { TaskProgressEvent event ->
                    assert event != null
                    result << event
                }.run()
        }

        then:
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
                test                : ['started', 'skipped']
        ]
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "task progress event can be received if tasks are executed in parallel"() {
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
                connection.newBuild()
                        .withArguments("-Dorg.gradle.parallel.intra=true", '--parallel', '--max-workers=2')
                        .forTasks('parallelSleep')
                        .addTaskProgressListener { TaskProgressEvent event ->
                    assert event != null
                    result << event
                }.run()
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
        buildFile << """task dummy()"""
        file("buildSrc/build.gradle") << """
            task taskInBuildSrc()
        """

        when:
        def result = []
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('dummy').addTaskProgressListener { TaskProgressEvent event ->
                assert event != null
                result << event
            }.run()
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
        buildFile << """task innerBuild(type:GradleBuild) {
            buildFile = file('other.gradle')
            tasks = ['innerTask']
        }"""
        file("other.gradle") << """
            task innerTask()
        """

        when:
        def result = []
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('innerBuild').addTaskProgressListener { TaskProgressEvent event ->
                assert event != null
                result << event
            }.run()
        }

        then:
        result.size() == 2 * tasks.size()
        assertUnorderedEvents(result, tasks)

        where:
        tasks = [
                ':innerBuild'                   : ['started', 'succeeded'],
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
                ':innerTask'                    : ['started', 'up-to-date']
        ]
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
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
                        assert event.result.successMessage == 'up-to-date'
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
