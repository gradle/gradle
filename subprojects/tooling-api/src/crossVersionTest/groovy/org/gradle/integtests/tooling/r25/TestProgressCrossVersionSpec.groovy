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
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.ListenerFailedException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.TestFailureResult
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestProgressEvent
import org.gradle.tooling.events.test.TestSkippedResult
import org.gradle.tooling.events.test.internal.DefaultTestFinishEvent
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class TestProgressCrossVersionSpec extends ToolingApiSpecification {

    def "receive test output"() {
        given:
        goodCode()
        file("src/test/java/example/MyTest2.java") << """
            package example;
            public class MyTest2 {
                @org.junit.Test public void foo() throws Exception {
                    System.out.println("Winged Hussars");
                    org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations.class).forTasks('test').addProgressListener(
                    new org.gradle.tooling.events.ProgressListener() {

                        @Override
                        void statusChanged(ProgressEvent event) {
                            if (event instanceof DefaultTestFinishEvent) {
                                println event.result.toString()
                            }
                        }
                    }

                ).withArguments("--info").get()
        }

        then:
        true
    }

    def "receive test progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some test task(s) to run first"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations.class).forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).get()
        }

        then: "test progress events must be forwarded to the attached listeners"
        !events.tests.empty
        events.operations == events.tests
    }

    def "receive test progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).run()
        }

        then: "test progress events must be forwarded to the attached listeners"
        !events.tests.empty
        events.operations == events.tests
    }

    def "receive current test progress event even if one of multiple test listeners throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        List<TestProgressEvent> resultsOfFirstListener = new ArrayList<TestProgressEvent>()
        List<TestProgressEvent> resultsOfLastListener = new ArrayList<TestProgressEvent>()
        def failure = new IllegalStateException("Throwing an exception on purpose")
        withConnection {
            ProjectConnection connection ->
                def build = connection.newBuild()
                build.forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfFirstListener << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST)).addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw failure
                    }
                }, EnumSet.of(OperationType.TEST)).addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfLastListener << (event as TestProgressEvent)
                    }
                }, EnumSet.of(OperationType.TEST))
                collectOutputs(build)
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

    def "receive test progress events for successful test run"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
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
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        events.tests.size() == 4              // root suite, test process suite, test class suite, test method
        events.tests == events.successful

        def rootSuite = events.operation("Gradle Test Run :test")
        rootSuite.descriptor.jvmTestKind == JvmTestKind.SUITE
        rootSuite.descriptor.name == 'Gradle Test Run :test'
        rootSuite.descriptor.displayName == 'Gradle Test Run :test'
        rootSuite.descriptor.suiteName == 'Gradle Test Run :test'
        rootSuite.descriptor.className == null
        rootSuite.descriptor.methodName == null
        rootSuite.descriptor.parent == null

        def workerSuite = events.operationMatches("Gradle Test Executor \\d+")
        workerSuite.descriptor.jvmTestKind == JvmTestKind.SUITE
        workerSuite.descriptor.name.matches 'Gradle Test Executor \\d+'
        workerSuite.descriptor.displayName.matches 'Gradle Test Executor \\d+'
        workerSuite.descriptor.suiteName.matches 'Gradle Test Executor \\d+'
        workerSuite.descriptor.className == null
        workerSuite.descriptor.methodName == null
        workerSuite.descriptor.parent == rootSuite.descriptor

        def testClass = events.operation("Test class example.MyTest")
        testClass.descriptor.jvmTestKind == JvmTestKind.SUITE
        testClass.descriptor.name == 'example.MyTest'
        testClass.descriptor.displayName == 'Test class example.MyTest'
        testClass.descriptor.suiteName == 'example.MyTest'
        testClass.descriptor.className == 'example.MyTest'
        testClass.descriptor.methodName == null
        testClass.descriptor.parent == workerSuite.descriptor

        def testMethod = events.operation("Test foo(example.MyTest)")
        testMethod.descriptor.jvmTestKind == JvmTestKind.ATOMIC
        testMethod.descriptor.name == 'foo'
        testMethod.descriptor.displayName == 'Test foo(example.MyTest)'
        testMethod.descriptor.suiteName == null
        testMethod.descriptor.className == 'example.MyTest'
        testMethod.descriptor.methodName == 'foo'
        testMethod.descriptor.parent == testClass.descriptor
    }

    def "receive test progress events for failed test run"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
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
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        events.tests.size() == 4              // root suite, test process suite, test class suite, test method

        def rootSuite = events.operation("Gradle Test Run :test")
        rootSuite.descriptor.jvmTestKind == JvmTestKind.SUITE
        rootSuite.descriptor.name == 'Gradle Test Run :test'
        rootSuite.descriptor.displayName == 'Gradle Test Run :test'
        rootSuite.descriptor.suiteName == 'Gradle Test Run :test'
        rootSuite.descriptor.className == null
        rootSuite.descriptor.methodName == null
        rootSuite.descriptor.parent == null
        rootSuite.result instanceof TestFailureResult
        rootSuite.result.failures.size() == 0

        def workerSuite = events.operationMatches("Gradle Test Executor \\d+")
        workerSuite.descriptor.jvmTestKind == JvmTestKind.SUITE
        workerSuite.descriptor.name.matches 'Gradle Test Executor \\d+'
        workerSuite.descriptor.displayName.matches 'Gradle Test Executor \\d+'
        workerSuite.descriptor.suiteName.matches 'Gradle Test Executor \\d+'
        workerSuite.descriptor.className == null
        workerSuite.descriptor.methodName == null
        workerSuite.descriptor.parent == rootSuite.descriptor
        workerSuite.result instanceof TestFailureResult
        workerSuite.result.failures.size() == 0

        def testClass = events.operation("Test class example.MyTest")
        testClass.descriptor.jvmTestKind == JvmTestKind.SUITE
        testClass.descriptor.name == 'example.MyTest'
        testClass.descriptor.displayName == 'Test class example.MyTest'
        testClass.descriptor.suiteName == 'example.MyTest'
        testClass.descriptor.className == 'example.MyTest'
        testClass.descriptor.methodName == null
        testClass.descriptor.parent == workerSuite.descriptor
        testClass.result instanceof TestFailureResult
        testClass.result.failures.size() == 0

        def testMethod = events.operation("Test foo(example.MyTest)")
        testMethod.descriptor.jvmTestKind == JvmTestKind.ATOMIC
        testMethod.descriptor.name == 'foo'
        testMethod.descriptor.displayName == 'Test foo(example.MyTest)'
        testMethod.descriptor.suiteName == null
        testMethod.descriptor.className == 'example.MyTest'
        testMethod.descriptor.methodName == 'foo'
        testMethod.descriptor.parent == testClass.descriptor
        testMethod.result instanceof TestFailureResult
        testMethod.result.failures.size() == 1
        testMethod.result.failures[0].message == 'broken'
        testMethod.result.failures[0].description.startsWith("java.lang.RuntimeException: broken")
        testMethod.result.failures[0].description.contains("at example.MyTest.foo(MyTest.java:6)")
        testMethod.result.failures[0].causes.size() == 1
        testMethod.result.failures[0].causes[0].message == 'nope'
        testMethod.result.failures[0].causes[0].causes.empty
    }

    def "receive test progress events for skipped test run"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
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
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        events.tests.size() == 4
        def testMethod = events.operation("Test foo(example.MyTest)")
        testMethod.result instanceof TestSkippedResult
    }

    def "test progress event ids are unique across multiple test workers"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
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
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).run()
        }

        then:
        events.tests.size() == (1 + 2 + 2 + 8) // 1 root suite, 2 worker processes, 2 tests classes, 8 tests
        events.tests == events.successful
        events.tests[0].descriptor.parent == null // 1 root suite with no further parent
        events.tests.tail().every { it.descriptor.parent != null }
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
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
            ${mavenCentralRepository()}
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
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).withArguments('--parallel').run()
        }

        then:
        events.tests.size() == 2 * (1 + 2 + 2 + 6)  // two test tasks with each: 1 root suite, 2 worker processes, 2 tests classes, 6 tests
        events.successful.size() == 8 // two test tasks with 4 tests
        events.failed.size() == 14 // two test tasks with: 1 root suite, 2 worker processes, 2 test classes, 2 tests
        events.tests.findAll { it.descriptor.parent == null }.size() == 2  // 2 root suites with no further parent
        events.tests.findAll { it.descriptor.name =~ 'Gradle Test Run :sub[1|2]:test' }.toSet().size() == 2  // 2 root suites for 2 tasks
        events.tests.findAll { it.descriptor.name =~ 'Gradle Test Executor \\d+' }.toSet().size() == 4       // 2 test processes for each task
    }

    def "top-level test operation has test task as parent if task listener is attached"() {
        given:
        goodCode()

        when: 'listening to test progress events and task listener is attached'
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TASK, OperationType.TEST)).run()
        }

        then: 'the parent of the root test progress event is the test task that triggered the tests'
        def test = events.operation("Task :test")
        events.tests[0].descriptor.parent == test.descriptor
        events.tests.tail().every { it.descriptor.parent instanceof TestOperationDescriptor }

        when: 'listening to test progress events and no task listener is attached'
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--rerun-tasks').forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).run()
        }

        then: 'the parent of the root test progress event is null'
        events.tests[0].descriptor.parent == null
        events.tests.tail().every { it.descriptor.parent instanceof TestOperationDescriptor }

        when: 'listening to test progress events and build operation listener is attached'
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--rerun-tasks').forTasks('test').addProgressListener(events, EnumSet.of(OperationType.GENERIC, OperationType.TEST)).run()
        }

        then: 'the parent of the root test progress event is null'
        events.tests[0].descriptor.parent == null
        events.tests.tail().every { it.descriptor.parent instanceof TestOperationDescriptor }
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            sourceCompatibility = 1.7
            ${mavenCentralRepository()}
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
