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
package org.gradle.integtests.tooling.r24

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.*
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.TestProgressListener
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.util.concurrent.ConcurrentLinkedQueue

class TestProgressCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion(">=2.4")
    @TargetGradleVersion(">=1.0-milestone-8 <2.4")
    def "ignores listeners when Gradle version does not generate test events"() {
        given:
        goodCode()

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener({ throw new RuntimeException() } as TestProgressListener).run()
        }

        then:
        noExceptionThrown()
    }

    @ToolingApiVersion(">=2.4")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some test task(s) to run first"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations.class).forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result.add(event)
                    }
                }).get()
        }

        then: "test progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.4")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result.add(event)
                    }
                }).run()
        }

        then: "test progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.4")
    @TargetGradleVersion(">=2.4")
    def "build aborts if a test listener throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw new IllegalStateException("Throwing an exception on purpose")
                    }
                }).run()
        }

        then: "build aborts if the test listener throws an exception"
        thrown(GradleConnectionException)
    }

    @ToolingApiVersion(">=2.4")
    @TargetGradleVersion(">=2.4")
    def "receive current test progress event even if one of multiple test listeners throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        List<ProgressEvent> resultsOfFirstListener = new ArrayList<ProgressEvent>()
        List<ProgressEvent> resultsOfLastListener = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfFirstListener.add(event)
                    }
                }).addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw new IllegalStateException("Throwing an exception on purpose")
                    }
                }).addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfLastListener.add(event)
                    }
                }).run()
        }

        then: "current test progress event must still be forwarded to the attached listeners even if one of the listeners throws an exception"
        thrown(GradleConnectionException)
        resultsOfFirstListener.size() == 1
        resultsOfLastListener.size() == 1
    }

    @ToolingApiVersion(">=2.4")
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
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof StartEvent &&
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
        testProcessStartedEvent instanceof StartEvent &&
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
        testClassStartedEvent instanceof StartEvent &&
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
        testStartedEvent instanceof StartEvent &&
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
        testSucceededEvent instanceof SuccessEvent &&
                testSucceededEvent.eventTime >= testSucceededEvent.result.outcome.endTime &&
                testSucceededEvent.displayName == "Test foo(example.MyTest) succeeded" &&
                testSucceededEvent.descriptor == testStartedEvent.descriptor &&
                testSucceededEvent.result.outcome.startTime > 0 &&
                testSucceededEvent.result.outcome.endTime > testSucceededEvent.result.outcome.startTime
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof SuccessEvent &&
                testClassSucceededEvent.eventTime >= testClassSucceededEvent.result.outcome.endTime &&
                testClassSucceededEvent.displayName == "Test class example.MyTest succeeded" &&
                testClassSucceededEvent.descriptor == testClassStartedEvent.descriptor &&
                testClassSucceededEvent.result.outcome.startTime > 0 &&
                testClassSucceededEvent.result.outcome.endTime > testClassSucceededEvent.result.outcome.startTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof SuccessEvent &&
                testProcessSucceededEvent.eventTime >= testProcessSucceededEvent.result.outcome.endTime &&
                testProcessSucceededEvent.displayName == "Gradle Test Executor 2 succeeded" &&
                testProcessSucceededEvent.descriptor == testProcessStartedEvent.descriptor &&
                testProcessSucceededEvent.result.outcome.startTime > 0 &&
                testProcessSucceededEvent.result.outcome.endTime > testProcessSucceededEvent.result.outcome.startTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof SuccessEvent &&
                rootSucceededEvent.eventTime >= rootSucceededEvent.result.outcome.endTime &&
                rootSucceededEvent.displayName == "Gradle Test Run :test succeeded" &&
                rootSucceededEvent.descriptor == rootStartedEvent.descriptor &&
                rootSucceededEvent.result.outcome.startTime > 0 &&
                rootSucceededEvent.result.outcome.endTime > rootSucceededEvent.result.outcome.startTime
    }

    @ToolingApiVersion(">=2.4")
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
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof StartEvent &&
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
        testProcessStartedEvent instanceof StartEvent &&
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
        testClassStartedEvent instanceof StartEvent &&
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
        testStartedEvent instanceof StartEvent &&
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
        testFailedEvent instanceof FailureEvent &&
                testFailedEvent.eventTime >= testFailedEvent.result.outcome.endTime &&
                testFailedEvent.displayName == "Test foo(example.MyTest) failed" &&
                testFailedEvent.descriptor == testStartedEvent.descriptor &&
                testFailedEvent.result.outcome.startTime > 0 &&
                testFailedEvent.result.outcome.endTime > testFailedEvent.result.outcome.startTime &&
                testFailedEvent.result.outcome.failures.size() == 1 &&
                testFailedEvent.result.outcome.failures[0].message == 'broken' &&
                testFailedEvent.result.outcome.failures[0].description.startsWith("java.lang.RuntimeException: broken") &&
                testFailedEvent.result.outcome.failures[0].description.contains("at example.MyTest.foo(MyTest.java:6)") &&
                testFailedEvent.result.outcome.failures[0].causes.size() == 1 &&
                testFailedEvent.result.outcome.failures[0].causes[0].message == 'nope' &&
                testFailedEvent.result.outcome.failures[0].causes[0].causes.empty
        def testClassFailedEvent = result[5]
        testClassFailedEvent instanceof FailureEvent &&
                testClassFailedEvent.eventTime >= testClassFailedEvent.result.outcome.endTime &&
                testClassFailedEvent.displayName == "Test class example.MyTest failed" &&
                testClassFailedEvent.descriptor == testClassStartedEvent.descriptor &&
                testClassFailedEvent.result.outcome.startTime > 0 &&
                testClassFailedEvent.result.outcome.endTime > testClassFailedEvent.result.outcome.startTime &&
                testClassFailedEvent.result.outcome.failures.size() == 0
        def testProcessFailedEvent = result[6]
        testProcessFailedEvent instanceof FailureEvent &&
                testProcessFailedEvent.eventTime >= testProcessFailedEvent.result.outcome.endTime &&
                testProcessFailedEvent.displayName == "Gradle Test Executor 2 failed" &&
                testProcessFailedEvent.descriptor == testProcessStartedEvent.descriptor &&
                testProcessFailedEvent.result.outcome.startTime > 0 &&
                testProcessFailedEvent.result.outcome.endTime > testProcessFailedEvent.result.outcome.startTime &&
                testProcessFailedEvent.result.outcome.failures.size() == 0
        def rootFailedEvent = result[7]
        rootFailedEvent instanceof FailureEvent &&
                rootFailedEvent.eventTime >= rootFailedEvent.result.outcome.endTime &&
                rootFailedEvent.displayName == "Gradle Test Run :test failed" &&
                rootFailedEvent.descriptor == rootStartedEvent.descriptor &&
                rootFailedEvent.result.outcome.startTime > 0 &&
                rootFailedEvent.result.outcome.endTime > rootFailedEvent.result.outcome.startTime &&
                rootFailedEvent.result.outcome.failures.size() == 0
    }

    @ToolingApiVersion(">=2.4")
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
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof StartEvent &&
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
        testProcessStartedEvent instanceof StartEvent &&
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
        testClassStartedEvent instanceof StartEvent &&
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
        testStartedEvent instanceof StartEvent &&
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
        testSkippedEvent instanceof SkippedEvent &&
                testSkippedEvent.eventTime > 0 &&
                testSkippedEvent.displayName == "Test foo(example.MyTest) skipped" &&
                testSkippedEvent.descriptor == testStartedEvent.descriptor
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof SuccessEvent &&
                testClassSucceededEvent.eventTime >= testClassSucceededEvent.result.outcome.endTime &&
                testClassSucceededEvent.displayName == "Test class example.MyTest succeeded" &&
                testClassSucceededEvent.descriptor == testClassStartedEvent.descriptor &&
                testClassSucceededEvent.result.outcome.startTime > 0 &&
                testClassSucceededEvent.result.outcome.endTime > testClassSucceededEvent.result.outcome.startTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof SuccessEvent &&
                testProcessSucceededEvent.eventTime >= testProcessSucceededEvent.result.outcome.endTime &&
                testProcessSucceededEvent.displayName == "Gradle Test Executor 2 succeeded" &&
                testProcessSucceededEvent.descriptor == testProcessStartedEvent.descriptor &&
                testProcessSucceededEvent.result.outcome.startTime > 0 &&
                testProcessSucceededEvent.result.outcome.endTime > testProcessSucceededEvent.result.outcome.startTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof SuccessEvent &&
                rootSucceededEvent.eventTime >= rootSucceededEvent.result.outcome.endTime &&
                rootSucceededEvent.displayName == "Gradle Test Run :test succeeded" &&
                rootSucceededEvent.descriptor == rootStartedEvent.descriptor &&
                rootSucceededEvent.result.outcome.startTime > 0 &&
                rootSucceededEvent.result.outcome.endTime > rootSucceededEvent.result.outcome.startTime
    }

    @ToolingApiVersion(">=2.4")
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
        Queue<ProgressEvent> result = new ConcurrentLinkedQueue<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
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
    @ToolingApiVersion(">=2.4")
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
        Queue<ProgressEvent> result = new ConcurrentLinkedQueue<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).withArguments('--parallel').run()
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
