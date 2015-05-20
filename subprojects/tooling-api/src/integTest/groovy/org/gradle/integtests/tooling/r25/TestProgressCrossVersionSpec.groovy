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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.*
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.util.concurrent.ConcurrentLinkedQueue

class TestProgressCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=1.0-milestone-8 <2.4")
    def "ignores listeners when Gradle version does not generate test events"() {
        given:
        goodCode()

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener({
                    throw new RuntimeException()
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        noExceptionThrown()
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some test task(s) to run first"
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations.class).forTasks('test').addProgressListener({ ProgressEvent event ->
                    result << (event as TestProgressEvent)
                }, EnumSet.of(OperationType.TEST)).get()
        }

        then: "test progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then: "test progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "build aborts if a test listener throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw new IllegalStateException("Throwing an exception on purpose")
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then: "build aborts if the test listener throws an exception"
        thrown(GradleConnectionException)
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "receive current test progress event even if one of multiple test listeners throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        List<TestProgressEvent> resultsOfFirstListener = new ArrayList<TestProgressEvent>()
        List<TestProgressEvent> resultsOfLastListener = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfFirstListener << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw new IllegalStateException("Throwing an exception on purpose")
                    }
                }, EnumSet.of(OperationType.TEST)).addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfLastListener << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then: "current test progress event must still be forwarded to the attached listeners even if one of the listeners throws an exception"
        thrown(GradleConnectionException)
        if (GradleVersion.version(targetDist.version.baseVersion.version) >= GradleVersion.version('2.5')) {
            assert resultsOfFirstListener.size() > 1
            assert resultsOfLastListener.size() > 1
        } else {
            resultsOfFirstListener.size() == 1
            resultsOfLastListener.size() == 1
        }
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events for successful test run"() {
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
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestStartEvent &&
            rootStartedEvent.eventTime > 0 &&
            rootStartedEvent.displayName == "Gradle Test Run :test started" &&
            rootStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            rootStartedEvent.descriptor.name == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.displayName == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.suiteName == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.className == null &&
            rootStartedEvent.descriptor.methodName == null &&
            rootStartedEvent.descriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestStartEvent &&
            testProcessStartedEvent.eventTime > 0 &&
            testProcessStartedEvent.displayName == "Gradle Test Executor 2 started" &&
            testProcessStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            testProcessStartedEvent.descriptor.name == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.displayName == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.suiteName == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.className == null &&
            testProcessStartedEvent.descriptor.methodName == null &&
            testProcessStartedEvent.descriptor.parent == rootStartedEvent.descriptor
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestStartEvent &&
            testClassStartedEvent.eventTime > 0 &&
            testClassStartedEvent.displayName == "Test class example.MyTest started" &&
            testClassStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            testClassStartedEvent.descriptor.name == 'example.MyTest' &&
            testClassStartedEvent.descriptor.displayName == 'Test class example.MyTest' &&
            testClassStartedEvent.descriptor.suiteName == 'example.MyTest' &&
            testClassStartedEvent.descriptor.className == 'example.MyTest' &&
            testClassStartedEvent.descriptor.methodName == null &&
            testClassStartedEvent.descriptor.parent == testProcessStartedEvent.descriptor
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartEvent &&
            testStartedEvent.eventTime > 0 &&
            testStartedEvent.displayName == "Test foo(example.MyTest) started" &&
            testStartedEvent.descriptor.jvmTestKind == JvmTestKind.ATOMIC &&
            testStartedEvent.descriptor.name == 'foo' &&
            testStartedEvent.descriptor.displayName == 'Test foo(example.MyTest)' &&
            testStartedEvent.descriptor.suiteName == null &&
            testStartedEvent.descriptor.className == 'example.MyTest' &&
            testStartedEvent.descriptor.methodName == 'foo' &&
            testStartedEvent.descriptor.parent == testClassStartedEvent.descriptor
        def testSucceededEvent = result[4]
        testSucceededEvent instanceof TestFinishEvent &&
            testSucceededEvent.eventTime >= testSucceededEvent.result.endTime &&
            testSucceededEvent.displayName == "Test foo(example.MyTest) succeeded" &&
            testSucceededEvent.descriptor == testStartedEvent.descriptor &&
            testSucceededEvent.result instanceof TestSuccessResult &&
            testSucceededEvent.result.startTime == testStartedEvent.eventTime &&
            testSucceededEvent.result.endTime > testSucceededEvent.result.startTime &&
            testSucceededEvent.result.endTime == testSucceededEvent.eventTime
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof TestFinishEvent &&
            testClassSucceededEvent.eventTime >= testClassSucceededEvent.result.endTime &&
            testClassSucceededEvent.displayName == "Test class example.MyTest succeeded" &&
            testClassSucceededEvent.descriptor == testClassStartedEvent.descriptor &&
            testClassSucceededEvent.result instanceof TestSuccessResult &&
            testClassSucceededEvent.result.startTime == testClassStartedEvent.eventTime &&
            testClassSucceededEvent.result.endTime > testClassSucceededEvent.result.startTime &&
            testClassSucceededEvent.result.endTime == testClassSucceededEvent.eventTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof TestFinishEvent &&
            testProcessSucceededEvent.eventTime >= testProcessSucceededEvent.result.endTime &&
            testProcessSucceededEvent.displayName == "Gradle Test Executor 2 succeeded" &&
            testProcessSucceededEvent.descriptor == testProcessStartedEvent.descriptor &&
            testProcessSucceededEvent.result instanceof TestSuccessResult &&
            testProcessSucceededEvent.result.startTime == testProcessStartedEvent.eventTime &&
            testProcessSucceededEvent.result.endTime > testProcessSucceededEvent.result.startTime &&
            testProcessSucceededEvent.result.endTime == testProcessSucceededEvent.eventTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof TestFinishEvent &&
            rootSucceededEvent.eventTime >= rootSucceededEvent.result.endTime &&
            rootSucceededEvent.displayName == "Gradle Test Run :test succeeded" &&
            rootSucceededEvent.descriptor == rootStartedEvent.descriptor &&
            rootSucceededEvent.result instanceof TestSuccessResult &&
            rootSucceededEvent.result.startTime == rootStartedEvent.eventTime &&
            rootSucceededEvent.result.endTime > rootSucceededEvent.result.startTime &&
            rootSucceededEvent.result.endTime == rootSucceededEvent.eventTime
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events for failed test run"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
            test.ignoreFailures = true
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
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestStartEvent &&
            rootStartedEvent.eventTime > 0 &&
            rootStartedEvent.displayName == "Gradle Test Run :test started" &&
            rootStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            rootStartedEvent.descriptor.name == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.displayName == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.suiteName == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.className == null &&
            rootStartedEvent.descriptor.methodName == null &&
            rootStartedEvent.descriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestStartEvent &&
            testProcessStartedEvent.eventTime > 0 &&
            testProcessStartedEvent.displayName == "Gradle Test Executor 2 started" &&
            testProcessStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            testProcessStartedEvent.descriptor.name == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.displayName == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.suiteName == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.className == null &&
            testProcessStartedEvent.descriptor.methodName == null &&
            testProcessStartedEvent.descriptor.parent == rootStartedEvent.descriptor
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestStartEvent &&
            testClassStartedEvent.eventTime > 0 &&
            testClassStartedEvent.displayName == "Test class example.MyTest started" &&
            testClassStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            testClassStartedEvent.descriptor.name == 'example.MyTest' &&
            testClassStartedEvent.descriptor.displayName == 'Test class example.MyTest' &&
            testClassStartedEvent.descriptor.suiteName == 'example.MyTest' &&
            testClassStartedEvent.descriptor.className == 'example.MyTest' &&
            testClassStartedEvent.descriptor.methodName == null &&
            testClassStartedEvent.descriptor.parent == testProcessStartedEvent.descriptor
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartEvent &&
            testStartedEvent.eventTime > 0 &&
            testStartedEvent.displayName == "Test foo(example.MyTest) started" &&
            testStartedEvent.descriptor.jvmTestKind == JvmTestKind.ATOMIC &&
            testStartedEvent.descriptor.name == 'foo' &&
            testStartedEvent.descriptor.displayName == 'Test foo(example.MyTest)' &&
            testStartedEvent.descriptor.suiteName == null &&
            testStartedEvent.descriptor.className == 'example.MyTest' &&
            testStartedEvent.descriptor.methodName == 'foo' &&
            testStartedEvent.descriptor.parent == testClassStartedEvent.descriptor
        def testFailedEvent = result[4]
        testFailedEvent instanceof TestFinishEvent &&
            testFailedEvent.eventTime >= testFailedEvent.result.endTime &&
            testFailedEvent.displayName == "Test foo(example.MyTest) failed" &&
            testFailedEvent.descriptor == testStartedEvent.descriptor &&
            testFailedEvent.result instanceof TestFailureResult &&
            testFailedEvent.result.startTime == testStartedEvent.eventTime &&
            testFailedEvent.result.endTime == testFailedEvent.eventTime &&
            testFailedEvent.result.failures.size() == 1 &&
            testFailedEvent.result.failures[0].message == 'broken' &&
            testFailedEvent.result.failures[0].description.startsWith("java.lang.RuntimeException: broken") &&
            testFailedEvent.result.failures[0].description.contains("at example.MyTest.foo(MyTest.java:6)") &&
            testFailedEvent.result.failures[0].causes.size() == 1 &&
            testFailedEvent.result.failures[0].causes[0].message == 'nope' &&
            testFailedEvent.result.failures[0].causes[0].causes.empty
        def testClassFailedEvent = result[5]
        testClassFailedEvent instanceof TestFinishEvent &&
            testClassFailedEvent.eventTime >= testClassFailedEvent.result.endTime &&
            testClassFailedEvent.displayName == "Test class example.MyTest failed" &&
            testClassFailedEvent.descriptor == testClassStartedEvent.descriptor &&
            testClassFailedEvent.result instanceof TestFailureResult &&
            testClassFailedEvent.result.startTime == testClassStartedEvent.eventTime &&
            testClassFailedEvent.result.endTime == testClassFailedEvent.eventTime &&
            testClassFailedEvent.result.failures.size() == 0
        def testProcessFailedEvent = result[6]
        testProcessFailedEvent instanceof TestFinishEvent &&
            testProcessFailedEvent.eventTime >= testProcessFailedEvent.result.endTime &&
            testProcessFailedEvent.displayName == "Gradle Test Executor 2 failed" &&
            testProcessFailedEvent.descriptor == testProcessStartedEvent.descriptor &&
            testProcessFailedEvent.result instanceof TestFailureResult &&
            testProcessFailedEvent.result.startTime == testProcessStartedEvent.eventTime &&
            testProcessFailedEvent.result.endTime == testProcessFailedEvent.eventTime &&
            testProcessFailedEvent.result.failures.size() == 0
        def rootFailedEvent = result[7]
        rootFailedEvent instanceof TestFinishEvent &&
            rootFailedEvent.eventTime >= rootFailedEvent.result.endTime &&
            rootFailedEvent.displayName == "Gradle Test Run :test failed" &&
            rootFailedEvent.descriptor == rootStartedEvent.descriptor &&
            rootFailedEvent.result instanceof TestFailureResult &&
            rootFailedEvent.result.startTime == rootStartedEvent.eventTime &&
            rootFailedEvent.result.endTime == rootFailedEvent.eventTime &&
            rootFailedEvent.result.failures.size() == 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events for skipped test run"() {
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
                @org.junit.Ignore @org.junit.Test public void foo() throws Exception {
                     Thread.sleep(100);  // sleep for a moment to ensure test duration is > 0 (due to limited clock resolution)
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when:
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestStartEvent &&
            rootStartedEvent.eventTime > 0 &&
            rootStartedEvent.displayName == "Gradle Test Run :test started" &&
            rootStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            rootStartedEvent.descriptor.name == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.displayName == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.suiteName == 'Gradle Test Run :test' &&
            rootStartedEvent.descriptor.className == null &&
            rootStartedEvent.descriptor.methodName == null &&
            rootStartedEvent.descriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestStartEvent &&
            testProcessStartedEvent.eventTime > 0 &&
            testProcessStartedEvent.displayName == "Gradle Test Executor 2 started" &&
            testProcessStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            testProcessStartedEvent.descriptor.name == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.displayName == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.suiteName == 'Gradle Test Executor 2' &&
            testProcessStartedEvent.descriptor.className == null &&
            testProcessStartedEvent.descriptor.methodName == null &&
            testProcessStartedEvent.descriptor.parent == rootStartedEvent.descriptor
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestStartEvent &&
            testClassStartedEvent.eventTime > 0 &&
            testClassStartedEvent.displayName == "Test class example.MyTest started" &&
            testClassStartedEvent.descriptor.jvmTestKind == JvmTestKind.SUITE &&
            testClassStartedEvent.descriptor.name == 'example.MyTest' &&
            testClassStartedEvent.descriptor.displayName == "Test class example.MyTest" &&
            testClassStartedEvent.descriptor.suiteName == 'example.MyTest' &&
            testClassStartedEvent.descriptor.className == 'example.MyTest' &&
            testClassStartedEvent.descriptor.methodName == null &&
            testClassStartedEvent.descriptor.parent == testProcessStartedEvent.descriptor
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartEvent &&
            testStartedEvent.eventTime > 0 &&
            testStartedEvent.displayName == "Test foo(example.MyTest) started" &&
            testStartedEvent.descriptor.jvmTestKind == JvmTestKind.ATOMIC &&
            testStartedEvent.descriptor.name == 'foo' &&
            testStartedEvent.descriptor.displayName == 'Test foo(example.MyTest)' &&
            testStartedEvent.descriptor.suiteName == null &&
            testStartedEvent.descriptor.className == 'example.MyTest' &&
            testStartedEvent.descriptor.methodName == 'foo' &&
            testStartedEvent.descriptor.parent == testClassStartedEvent.descriptor
        def testSkippedEvent = result[4]
        testSkippedEvent instanceof TestFinishEvent &&
            testSkippedEvent.eventTime > 0 &&
            testSkippedEvent.displayName == "Test foo(example.MyTest) skipped" &&
            testSkippedEvent.descriptor == testStartedEvent.descriptor &&
            testSkippedEvent.result instanceof TestSkippedResult &&
            testSkippedEvent.result.startTime == testStartedEvent.eventTime &&
            testSkippedEvent.result.endTime == testSkippedEvent.eventTime
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof TestFinishEvent &&
            testClassSucceededEvent.eventTime >= testClassSucceededEvent.result.endTime &&
            testClassSucceededEvent.displayName == "Test class example.MyTest succeeded" &&
            testClassSucceededEvent.descriptor == testClassStartedEvent.descriptor &&
            testClassSucceededEvent.result instanceof TestSuccessResult &&
            testClassSucceededEvent.result.startTime == testClassStartedEvent.eventTime &&
            testClassSucceededEvent.result.endTime == testClassSucceededEvent.eventTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof TestFinishEvent &&
            testProcessSucceededEvent.eventTime >= testProcessSucceededEvent.result.endTime &&
            testProcessSucceededEvent.displayName == "Gradle Test Executor 2 succeeded" &&
            testProcessSucceededEvent.descriptor == testProcessStartedEvent.descriptor &&
            testProcessSucceededEvent.result instanceof TestSuccessResult &&
            testProcessSucceededEvent.result.startTime == testProcessStartedEvent.eventTime &&
            testProcessSucceededEvent.result.endTime == testProcessSucceededEvent.eventTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof TestFinishEvent &&
            rootSucceededEvent.eventTime >= rootSucceededEvent.result.endTime &&
            rootSucceededEvent.displayName == "Gradle Test Run :test succeeded" &&
            rootSucceededEvent.descriptor == rootStartedEvent.descriptor &&
            rootSucceededEvent.result instanceof TestSuccessResult &&
            rootSucceededEvent.result.startTime == rootStartedEvent.eventTime &&
            rootSucceededEvent.result.endTime == rootSucceededEvent.eventTime
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "test progress event ids are unique across multiple test workers"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
            test.maxParallelForks = 2
        """

        file("src/test/java/example/MyTest1.java") << """
            package example;
            public class MyTest1 {
                @org.junit.Test public void alpha() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void beta() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void gamma() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void delta() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
        file("src/test/java/example/MyTest2.java") << """
            package example;
            public class MyTest2 {
                @org.junit.Test public void one() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void two() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void three() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void four() throws Exception {
                     Thread.sleep(100);
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when:
        Queue<TestProgressEvent> result = new ConcurrentLinkedQueue<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then: "start and end event is sent for each node in the test tree"
        result.size() % 2 == 0                // same number of start events as finish events
        result.size() == 2 * (1 + 2 + 2 + 8)  // 1 root suite, 2 test processes, 2 tests classes, 8 tests (each with a start and finish event)

        then: "each node in the test tree has its own description"
        result.collect { it.descriptor }.toSet().size() == 13

        then: "number of nodes under the root suite is equal to the number of test worker processes"
        result.findAll { it.descriptor.parent == null }.toSet().size() == 2  // 1 root suite with no further parent (start & finish events)
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.4")
    def "test progress event ids are unique across multiple test tasks, even when run in parallel"() {
        given:
        projectDir.createFile('settings.gradle') << """
            include ':sub1'
            include ':sub2'
        """
        projectDir.createFile('build.gradle')

        [projectDir.createDir('sub1'), projectDir.createDir('sub2')].eachWithIndex { TestFile it, def index ->
            it.file('build.gradle') << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true
            test.maxParallelForks = 2
            test.ignoreFailures = true
        """
            it.file("src/test/java/sub/MyUnitTest1${index}.java") << """
            package sub;
            public class MyUnitTest1$index {
                @org.junit.Test public void alpha() throws Exception {
                     Thread.sleep(300);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void beta() throws Exception {
                     Thread.sleep(1000);
                     org.junit.Assert.assertEquals(2, 1);
                }
            }
        """
            it.file("src/test/java/sub/MyUnitTest2${index}.java") << """
            package sub;
            public class MyUnitTest2$index {
                @org.junit.Test public void one() throws Exception {
                     Thread.sleep(1000);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void two() throws Exception {
                     Thread.sleep(300);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void three() throws Exception {
                     Thread.sleep(300);
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void four() throws Exception {
                     Thread.sleep(300);
                     org.junit.Assert.assertEquals(3, 1);
                }
            }
        """
        }

        when:
        Queue<TestProgressEvent> result = new ConcurrentLinkedQueue<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).withArguments('--parallel').run()
        }

        then: "start and end event is sent for each node in the test tree"
        result.size() % 2 == 0                    // same number of start events as finish events
        result.size() == 2 * 2 * (1 + 2 + 2 + 6)  // two test tasks with each: 1 root suite, 2 test processes, 2 tests classes, 6 tests (each with a start and finish event)

        then: "each node in the test tree has its own description"
        result.collect { it.descriptor }.toSet().size() == 2 * 11

        then: "number of nodes under the root suite is equal to the number of test worker processes"
        result.findAll { it.descriptor.parent == null }.toSet().size() == 4  // 2 root suites with no further parent (start & finish events)

        then: "names for root suites and worker suites are consistent"
        result.findAll { it.descriptor.name =~ 'Gradle Test Run :sub[1|2]:test' }.toSet().size() == 4  // 2 root suites for 2 tasks (start & finish events)
        result.findAll { it.descriptor.name =~ 'Gradle Test Executor \\d+' }.toSet().size() == 8       // 2 test processes for each task (start & finish events)
    }

    @TargetGradleVersion(">=2.5")
    @ToolingApiVersion(">=2.4")
    @NotYetImplemented
    def "should receive test events from buildSrc"() {
        buildFile << """task dummy()"""
        file("buildSrc/build.gradle") << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """
        file("buildSrc/src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when:
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('dummy').addProgressListener({ ProgressEvent event ->
                result << (event as TestProgressEvent)
            }, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        !result.empty
    }

    @TargetGradleVersion(">=2.5")
    @ToolingApiVersion(">=2.5")
    def "top-level test operation has test task as parent iff task listener is attached"() {
        given:
        goodCode()

        when: 'listening to test progress events and task listener is attached'
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        if (event instanceof TestProgressEvent) {
                            result << event
                        }
                    }
                }, EnumSet.of(OperationType.TASK, OperationType.TEST)).run()
        }

        then: 'the parent of the root test progress event is the test task that triggered the tests'
        !result.isEmpty()
        [result.first(), result.last()].each { def event ->
            assert event.descriptor.parent instanceof TaskOperationDescriptor
            assert event.descriptor.parent.name == 'test'
        }

        when: 'listening to test progress events and no task listener is attached'
        result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--rerun-tasks').forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then: 'the parent of the root test progress event is null'
        !result.isEmpty()
        [result.first(), result.last()].each { def event ->
            assert event.descriptor.parent == null
        }
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
}
