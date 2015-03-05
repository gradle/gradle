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
import org.gradle.tooling.*

class TestProgressCrossVersionSpec extends ToolingApiSpecification {

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

        file("src/test/java/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     Thread.sleep(100);  // sleep for a moment to ensure test duration is > 0 (due to limited clock resolution)
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when:
        List<TestProgressEvent> result = []
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        result << event
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestSuiteStartedEvent &&
                rootStartedEvent.descriptor.name == 'Test Run' &&
                rootStartedEvent.descriptor.className == null &&
                rootStartedEvent.descriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestSuiteStartedEvent &&
                testProcessStartedEvent.descriptor.name == 'Gradle Test Executor 2' &&
                testProcessStartedEvent.descriptor.className == null &&
                testProcessStartedEvent.descriptor.parent == null
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestSuiteStartedEvent &&
                testClassStartedEvent.descriptor.name == 'example.MyTest' &&
                testClassStartedEvent.descriptor.className == 'example.MyTest' &&
                testClassStartedEvent.descriptor.parent == null
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartedEvent &&
                testStartedEvent.descriptor.name == 'foo' &&
                testStartedEvent.descriptor.className == 'example.MyTest' &&
                testStartedEvent.descriptor.parent == null
        def testSucceededEvent = result[4]
        testSucceededEvent instanceof TestSucceededEvent &&
                testSucceededEvent.descriptor.name == 'foo' &&
                testSucceededEvent.descriptor.className == 'example.MyTest' &&
                testSucceededEvent.descriptor.parent == null &&
                ((TestSucceededEvent) testSucceededEvent).result.startTime > 0 &&
                ((TestSucceededEvent) testSucceededEvent).result.endTime > ((TestSucceededEvent) testSucceededEvent).result.startTime
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof TestSuiteSucceededEvent &&
                testClassSucceededEvent.descriptor.name == 'example.MyTest' &&
                testClassSucceededEvent.descriptor.className == 'example.MyTest' &&
                testClassSucceededEvent.descriptor.parent == null &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof TestSuiteSucceededEvent &&
                testProcessSucceededEvent.descriptor.name == 'Gradle Test Executor 2' &&
                testProcessSucceededEvent.descriptor.className == null &&
                testProcessSucceededEvent.descriptor.parent == null &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof TestSuiteSucceededEvent &&
                rootSucceededEvent.descriptor.name == 'Test Run' &&
                rootSucceededEvent.descriptor.className == null &&
                rootSucceededEvent.descriptor.parent == null &&
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
        List<TestProgressEvent> result = []
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        result << event
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestSuiteStartedEvent &&
                rootStartedEvent.descriptor.name == 'Test Run' &&
                rootStartedEvent.descriptor.className == null &&
                rootStartedEvent.descriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestSuiteStartedEvent &&
                testProcessStartedEvent.descriptor.name == 'Gradle Test Executor 2' &&
                testProcessStartedEvent.descriptor.className == null &&
                testProcessStartedEvent.descriptor.parent == null
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestSuiteStartedEvent &&
                testClassStartedEvent.descriptor.name == 'example.MyTest' &&
                testClassStartedEvent.descriptor.className == 'example.MyTest' &&
                testClassStartedEvent.descriptor.parent == null
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartedEvent &&
                testStartedEvent.descriptor.name == 'foo' &&
                testStartedEvent.descriptor.className == 'example.MyTest' &&
                testStartedEvent.descriptor.parent == null
        def testFailedEvent = result[4]
        testFailedEvent instanceof TestFailedEvent &&
                testFailedEvent.descriptor.name == 'foo' &&
                testFailedEvent.descriptor.className == 'example.MyTest' &&
                testFailedEvent.descriptor.parent == null &&
                ((TestFailedEvent) testFailedEvent).result.startTime > 0 &&
                ((TestFailedEvent) testFailedEvent).result.endTime > ((TestFailedEvent) testFailedEvent).result.startTime &&
                ((TestFailedEvent) testFailedEvent).result.exceptions.findAll { it.class == AssertionError }.size() == 1
        def testClassFailedEvent = result[5]
        testClassFailedEvent instanceof TestSuiteFailedEvent &&
                testClassFailedEvent.descriptor.name == 'example.MyTest' &&
                testClassFailedEvent.descriptor.className == 'example.MyTest' &&
                testClassFailedEvent.descriptor.parent == null &&
                ((TestSuiteFailedEvent) testClassFailedEvent).result.startTime > 0 &&
                ((TestSuiteFailedEvent) testClassFailedEvent).result.endTime > ((TestSuiteFailedEvent) testClassFailedEvent).result.startTime &&
                ((TestSuiteFailedEvent) testClassFailedEvent).result.exceptions.size() == 0
        def testProcessFailedEvent = result[6]
        testProcessFailedEvent instanceof TestSuiteFailedEvent &&
                testProcessFailedEvent.descriptor.name == 'Gradle Test Executor 2' &&
                testProcessFailedEvent.descriptor.className == null &&
                testProcessFailedEvent.descriptor.parent == null &&
                ((TestSuiteFailedEvent) testProcessFailedEvent).result.startTime > 0 &&
                ((TestSuiteFailedEvent) testProcessFailedEvent).result.endTime > ((TestSuiteFailedEvent) testProcessFailedEvent).result.startTime &&
                ((TestSuiteFailedEvent) testProcessFailedEvent).result.exceptions.size() == 0
        def rootFailedEvent = result[7]
        rootFailedEvent instanceof TestSuiteFailedEvent &&
                rootFailedEvent.descriptor.name == 'Test Run' &&
                rootFailedEvent.descriptor.className == null &&
                rootFailedEvent.descriptor.parent == null &&
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
        List<TestProgressEvent> result = []
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                    @Override
                    void statusChanged(TestProgressEvent event) {
                        result << event
                    }
                }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestSuiteStartedEvent &&
                rootStartedEvent.descriptor.name == 'Test Run' &&
                rootStartedEvent.descriptor.className == null &&
                rootStartedEvent.descriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestSuiteStartedEvent &&
                testProcessStartedEvent.descriptor.name == 'Gradle Test Executor 2' &&
                testProcessStartedEvent.descriptor.className == null &&
                testProcessStartedEvent.descriptor.parent == null
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestSuiteStartedEvent &&
                testClassStartedEvent.descriptor.name == 'example.MyTest' &&
                testClassStartedEvent.descriptor.className == 'example.MyTest' &&
                testClassStartedEvent.descriptor.parent == null
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartedEvent &&
                testStartedEvent.descriptor.name == 'foo' &&
                testStartedEvent.descriptor.className == 'example.MyTest' &&
                testStartedEvent.descriptor.parent == null
        def testSkippedEvent = result[4]
        testSkippedEvent instanceof TestSkippedEvent &&
                testSkippedEvent.descriptor.name == 'foo' &&
                testSkippedEvent.descriptor.className == 'example.MyTest' &&
                testSkippedEvent.descriptor.parent == null
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof TestSuiteSucceededEvent &&
                testClassSucceededEvent.descriptor.name == 'example.MyTest' &&
                testClassSucceededEvent.descriptor.className == 'example.MyTest' &&
                testClassSucceededEvent.descriptor.parent == null &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testClassSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testClassSucceededEvent).result.startTime
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof TestSuiteSucceededEvent &&
                testProcessSucceededEvent.descriptor.name == 'Gradle Test Executor 2' &&
                testProcessSucceededEvent.descriptor.className == null &&
                testProcessSucceededEvent.descriptor.parent == null &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) testProcessSucceededEvent).result.startTime
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof TestSuiteSucceededEvent &&
                rootSucceededEvent.descriptor.name == 'Test Run' &&
                rootSucceededEvent.descriptor.className == null &&
                rootSucceededEvent.descriptor.parent == null &&
                ((TestSuiteSucceededEvent) rootSucceededEvent).result.startTime > 0 &&
                ((TestSuiteSucceededEvent) rootSucceededEvent).result.endTime > ((TestSuiteSucceededEvent) rootSucceededEvent).result.startTime
    }

}
