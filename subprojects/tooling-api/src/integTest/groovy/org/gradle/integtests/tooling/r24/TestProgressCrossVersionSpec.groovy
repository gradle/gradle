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
import org.gradle.tooling.*
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.util.concurrent.ConcurrentLinkedQueue

class TestProgressCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=2.4")
    @TargetGradleVersion(">=2.4")
    def "receive test progress events when requesting a model"() {
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
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when: "asking for a model and specifying some test task(s) to run first"
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations.class).forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
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

        when: "launching a build"
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
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

        when: "launching a build"
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
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

        when: "launching a build"
        List<TestProgressEvent> resultsOfFirstListener = new ArrayList<TestProgressEvent>()
        List<TestProgressEvent> resultsOfLastListener = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        resultsOfFirstListener.add(event)
                    }
                }).addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        throw new IllegalStateException("Throwing an exception on purpose")
                    }
                }).addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
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
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestSuiteStartedEvent &&
                rootStartedEvent.eventTime > 0 &&
                rootStartedEvent.description == "TestSuite 'Gradle Test Run :test' started." &&
                rootStartedEvent.testDescriptor.name == 'Gradle Test Run :test' &&
                rootStartedEvent.testDescriptor.className == null &&
                rootStartedEvent.testDescriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestSuiteStartedEvent &&
                testProcessStartedEvent.eventTime > 0 &&
                testProcessStartedEvent.description == "TestSuite 'Gradle Test Executor 2' started." &&
                testProcessStartedEvent.testDescriptor.name == 'Gradle Test Executor 2' &&
                testProcessStartedEvent.testDescriptor.className == null &&
                testProcessStartedEvent.testDescriptor.parent == rootStartedEvent.testDescriptor
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestSuiteStartedEvent &&
                testClassStartedEvent.eventTime > 0 &&
                testClassStartedEvent.description == "TestSuite 'example.MyTest' started." &&
                testClassStartedEvent.testDescriptor.name == 'example.MyTest' &&
                testClassStartedEvent.testDescriptor.className == 'example.MyTest' &&
                testClassStartedEvent.testDescriptor.parent == testProcessStartedEvent.testDescriptor
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartedEvent &&
                testStartedEvent.eventTime > 0 &&
                testStartedEvent.description == "Test 'foo' started." &&
                testStartedEvent.testDescriptor.name == 'foo' &&
                testStartedEvent.testDescriptor.className == 'example.MyTest' &&
                testStartedEvent.testDescriptor.parent == testClassStartedEvent.testDescriptor
        def testSucceededEvent = result[4]
        testSucceededEvent instanceof TestSucceededEvent &&
                testSucceededEvent.eventTime == ((TestSucceededEvent) testSucceededEvent).result.endTime &&
                testSucceededEvent.description == "Test 'foo' succeeded." &&
                testSucceededEvent.testDescriptor == testStartedEvent.testDescriptor &&
                ((TestSucceededEvent) testSucceededEvent).result.startTime > 0 &&
                ((TestSucceededEvent) testSucceededEvent).result.endTime > ((TestSucceededEvent) testSucceededEvent).result.startTime
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof TestSuiteSucceededEvent &&
                testClassSucceededEvent.eventTime == ((TestSuiteSucceededEvent) testClassSucceededEvent).result.endTime &&
                testClassSucceededEvent.description == "TestSuite 'example.MyTest' succeeded." &&
                testClassSucceededEvent.testDescriptor == testClassStartedEvent.testDescriptor &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof TestSuiteSucceededEvent &&
                testProcessSucceededEvent.eventTime == ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.endTime &&
                testProcessSucceededEvent.description == "TestSuite 'Gradle Test Executor 2' succeeded." &&
                testProcessSucceededEvent.testDescriptor == testProcessStartedEvent.testDescriptor &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof TestSuiteSucceededEvent &&
                rootSucceededEvent.eventTime == ((TestSuiteSucceededEvent) rootSucceededEvent).result.endTime &&
                rootSucceededEvent.description == "TestSuite 'Gradle Test Run :test' succeeded." &&
                rootSucceededEvent.testDescriptor == rootStartedEvent.testDescriptor &&
                ((TestSuiteSucceededEvent) rootSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) rootSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) rootSucceededEvent).result.startTime
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
                     org.junit.Assert.assertEquals(1, 2);
                }
            }
        """

        when:
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestSuiteStartedEvent &&
                rootStartedEvent.eventTime > 0 &&
                rootStartedEvent.description == "TestSuite 'Gradle Test Run :test' started." &&
                rootStartedEvent.testDescriptor.name == 'Gradle Test Run :test' &&
                rootStartedEvent.testDescriptor.className == null &&
                rootStartedEvent.testDescriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestSuiteStartedEvent &&
                testProcessStartedEvent.eventTime > 0 &&
                testProcessStartedEvent.description == "TestSuite 'Gradle Test Executor 2' started." &&
                testProcessStartedEvent.testDescriptor.name == 'Gradle Test Executor 2' &&
                testProcessStartedEvent.testDescriptor.className == null &&
                testProcessStartedEvent.testDescriptor.parent == rootStartedEvent.testDescriptor
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestSuiteStartedEvent &&
                testClassStartedEvent.eventTime > 0 &&
                testClassStartedEvent.description == "TestSuite 'example.MyTest' started." &&
                testClassStartedEvent.testDescriptor.name == 'example.MyTest' &&
                testClassStartedEvent.testDescriptor.className == 'example.MyTest' &&
                testClassStartedEvent.testDescriptor.parent == testProcessStartedEvent.testDescriptor
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartedEvent &&
                testStartedEvent.eventTime > 0 &&
                testStartedEvent.description == "Test 'foo' started." &&
                testStartedEvent.testDescriptor.name == 'foo' &&
                testStartedEvent.testDescriptor.className == 'example.MyTest' &&
                testStartedEvent.testDescriptor.parent == testClassStartedEvent.testDescriptor
        def testFailedEvent = result[4]
        testFailedEvent instanceof TestFailedEvent &&
                testFailedEvent.eventTime == ((TestFailedEvent) testFailedEvent).result.endTime &&
                testFailedEvent.description == "Test 'foo' failed." &&
                testFailedEvent.testDescriptor == testStartedEvent.testDescriptor &&
                ((TestFailedEvent) testFailedEvent).result.startTime > 0 &&
                ((TestFailedEvent) testFailedEvent).result.endTime > ((TestFailedEvent) testFailedEvent).result.startTime &&
                ((TestFailedEvent) testFailedEvent).result.exceptions.findAll { it.class == AssertionError }.size() == 1
        def testClassFailedEvent = result[5]
        testClassFailedEvent instanceof TestSuiteFailedEvent &&
                testClassFailedEvent.eventTime == ((TestSuiteFailedEvent) testClassFailedEvent).result.endTime &&
                testClassFailedEvent.description == "TestSuite 'example.MyTest' failed." &&
                testClassFailedEvent.testDescriptor == testClassStartedEvent.testDescriptor &&
                ((TestSuiteFailedEvent) testClassFailedEvent).result.startTime > 0 &&
                ((TestSuiteFailedEvent) testClassFailedEvent).result.endTime > ((TestSuiteFailedEvent) testClassFailedEvent).result.startTime &&
                ((TestSuiteFailedEvent) testClassFailedEvent).result.exceptions.size() == 0
        def testProcessFailedEvent = result[6]
        testProcessFailedEvent instanceof TestSuiteFailedEvent &&
                testProcessFailedEvent.eventTime == ((TestSuiteFailedEvent) testProcessFailedEvent).result.endTime &&
                testProcessFailedEvent.description == "TestSuite 'Gradle Test Executor 2' failed." &&
                testProcessFailedEvent.testDescriptor == testProcessStartedEvent.testDescriptor &&
                ((TestSuiteFailedEvent) testProcessFailedEvent).result.startTime > 0 &&
                ((TestSuiteFailedEvent) testProcessFailedEvent).result.endTime > ((TestSuiteFailedEvent) testProcessFailedEvent).result.startTime &&
                ((TestSuiteFailedEvent) testProcessFailedEvent).result.exceptions.size() == 0
        def rootFailedEvent = result[7]
        rootFailedEvent instanceof TestSuiteFailedEvent &&
                rootFailedEvent.eventTime == ((TestSuiteFailedEvent) rootFailedEvent).result.endTime &&
                rootFailedEvent.description == "TestSuite 'Gradle Test Run :test' failed." &&
                rootFailedEvent.testDescriptor == rootStartedEvent.testDescriptor &&
                ((TestSuiteFailedEvent) rootFailedEvent).result.startTime > 0 &&
                ((TestSuiteFailedEvent) rootFailedEvent).result.endTime > ((TestSuiteFailedEvent) rootFailedEvent).result.startTime &&
                ((TestSuiteFailedEvent) rootFailedEvent).result.exceptions.size() == 0
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
        List<TestProgressEvent> result = new ArrayList<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestSuiteStartedEvent &&
                rootStartedEvent.eventTime > 0 &&
                rootStartedEvent.description == "TestSuite 'Gradle Test Run :test' started." &&
                rootStartedEvent.testDescriptor.name == 'Gradle Test Run :test' &&
                rootStartedEvent.testDescriptor.className == null &&
                rootStartedEvent.testDescriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestSuiteStartedEvent &&
                testProcessStartedEvent.eventTime > 0 &&
                testProcessStartedEvent.description == "TestSuite 'Gradle Test Executor 2' started." &&
                testProcessStartedEvent.testDescriptor.name == 'Gradle Test Executor 2' &&
                testProcessStartedEvent.testDescriptor.className == null &&
                testProcessStartedEvent.testDescriptor.parent == rootStartedEvent.testDescriptor
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestSuiteStartedEvent &&
                testClassStartedEvent.eventTime > 0 &&
                testClassStartedEvent.description == "TestSuite 'example.MyTest' started." &&
                testClassStartedEvent.testDescriptor.name == 'example.MyTest' &&
                testClassStartedEvent.testDescriptor.className == 'example.MyTest' &&
                testClassStartedEvent.testDescriptor.parent == testProcessStartedEvent.testDescriptor
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartedEvent &&
                testStartedEvent.eventTime > 0 &&
                testStartedEvent.description == "Test 'foo' started." &&
                testStartedEvent.testDescriptor.name == 'foo' &&
                testStartedEvent.testDescriptor.className == 'example.MyTest' &&
                testStartedEvent.testDescriptor.parent == testClassStartedEvent.testDescriptor
        def testSkippedEvent = result[4]
        testSkippedEvent instanceof TestSkippedEvent &&
                testSkippedEvent.eventTime > 0 &&
                testSkippedEvent.description == "Test 'foo' skipped." &&
                testSkippedEvent.testDescriptor == testStartedEvent.testDescriptor
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof TestSuiteSucceededEvent &&
                testClassSucceededEvent.eventTime == ((TestSuiteSucceededEvent) testClassSucceededEvent).result.endTime &&
                testClassSucceededEvent.description == "TestSuite 'example.MyTest' succeeded." &&
                testClassSucceededEvent.testDescriptor == testClassStartedEvent.testDescriptor &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof TestSuiteSucceededEvent &&
                testProcessSucceededEvent.eventTime == ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.endTime &&
                testProcessSucceededEvent.description == "TestSuite 'Gradle Test Executor 2' succeeded." &&
                testProcessSucceededEvent.testDescriptor == testProcessStartedEvent.testDescriptor &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof TestSuiteSucceededEvent &&
                rootSucceededEvent.eventTime == ((TestSuiteSucceededEvent) rootSucceededEvent).result.endTime &&
                rootSucceededEvent.description == "TestSuite 'Gradle Test Run :test' succeeded." &&
                rootSucceededEvent.testDescriptor == rootStartedEvent.testDescriptor &&
                ((TestSuiteSucceededEvent) rootSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) rootSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) rootSucceededEvent).result.startTime
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
        Queue<TestProgressEvent> result = new ConcurrentLinkedQueue<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).run()
        }

        then: "start and end event is sent for each node in the test tree"
        result.size() % 2 == 0                // same number of start events as finish events
        result.size() == 2 * (1 + 2 + 2 + 8)  // 1 root suite, 2 test processes, 2 tests classes, 8 tests (each with a start and finish event)

        then: "each node in the test tree has its own description"
        result.collect { it.testDescriptor }.toSet().size() == 13

        then: "number of nodes under the root suite is equal to the number of test worker processes"
        result.findAll { it.testDescriptor.parent == null }.toSet().size() == 2  // 1 root suite with no further parent (start & finish events)
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
        Queue<TestProgressEvent> result = new ConcurrentLinkedQueue<TestProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        assert event != null
                        result.add(event)
                    }
                }).withArguments('--parallel').run()
        }

        then: "start and end event is sent for each node in the test tree"
        result.size() % 2 == 0                    // same number of start events as finish events
        result.size() == 2 * 2 * (1 + 2 + 2 + 6)  // two test tasks with each: 1 root suite, 2 test processes, 2 tests classes, 6 tests (each with a start and finish event)

        then: "each node in the test tree has its own description"
        result.collect { it.testDescriptor }.toSet().size() == 2 * 11

        then: "number of nodes under the root suite is equal to the number of test worker processes"
        result.findAll { it.testDescriptor.parent == null }.toSet().size() == 4  // 2 root suites with no further parent (start & finish events)

        then: "names for root suites and worker suites are consistent"
        result.findAll { it.testDescriptor.name =~ 'Gradle Test Run :sub[1|2]:test' }.toSet().size() == 4  // 2 root suites for 2 tasks (start & finish events)
        result.findAll { it.testDescriptor.name =~ 'Gradle Test Executor \\d+' }.toSet().size() == 8       // 2 test processes for each task (start & finish events)
    }

}
