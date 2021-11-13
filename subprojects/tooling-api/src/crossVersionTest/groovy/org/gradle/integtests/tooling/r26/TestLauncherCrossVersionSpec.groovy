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

package org.gradle.integtests.tooling.r26

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.GradleException
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.ListenerFailedException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf
import spock.lang.Timeout

@Timeout(120)
class TestLauncherCrossVersionSpec extends TestLauncherSpec {
    public static final GradleVersion GRADLE_VERSION_34 = GradleVersion.version("3.4")

    def "test launcher api fires progress events"() {
        given:
        collectDescriptorsFromBuild()
        events.assertIsABuild()

        when:
        launchTests(testDescriptors("example.MyTest"));

        then:
        events.trees == events.tasks
        assertTaskOperationSuccessfulOrSkippedWithNoSource(":compileJava")
        assertTaskOperationSuccessfulOrSkippedWithNoSource(":processResources")
        events.operation("Task :classes").successful
        events.operation("Task :compileTestJava").successful
        assertTaskOperationSuccessfulOrSkippedWithNoSource(":processTestResources")
        events.operation("Task :testClasses").successful
        events.operation("Task :test").successful
        events.operation("Task :secondTest").successful
        events.operation("Gradle Test Run :test").successful
        events.operation("Gradle Test Run :secondTest").successful
        def testExecutorEvents = events.operations.findAll { it.descriptor.displayName.matches "Gradle Test Executor \\d+" }
        testExecutorEvents.size() == 2
        testExecutorEvents.every { it.successful }
        events.tests.findAll { it.descriptor.displayName == "Test class example.MyTest" }.size() == 2
        events.tests.findAll { it.descriptor.displayName == "Test foo(example.MyTest)" }.size() == 2
        events.tests.findAll { it.descriptor.displayName == "Test foo2(example.MyTest)" }.size() == 2
        if (supportsEfficientClassFiltering()) {
            events.tests.size() == 10
        } else {
            events.tests.findAll { it.descriptor.displayName == "Test class example2.MyOtherTest" }.size() == 2
            events.tests.size() == 12
        }
    }

    def "can run specific test class passed via test descriptor"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest"));
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        if (supportsEfficientClassFiltering()) {
            events.tests.size() == 10
            assertTestNotExecuted(className: "example2.MyOtherTest")
        } else {
            events.tests.size() == 12
            assertTestExecuted(className: "example2.MyOtherTest")
        }

        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
    }

    def "can run specific test method passed via test descriptor"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest", "foo"));
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        events.tests.size() == (supportsEfficientClassFiltering() ? 8 : 10)

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }

    def "runs only test task linked in test descriptor"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskExecuted(":secondTest")
        assertTaskNotExecuted(":test")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        events.tests.size() == (supportsEfficientClassFiltering() ? 5 : 6)

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }

    def "tests can be executed multiple times without task being up-to-date"() {
        given:
        collectDescriptorsFromBuild()
        and:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"))
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskNotUpToDate(":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTaskNotExecuted(":test")
    }

    @IgnoreIf({ GradleContextualExecuter.embedded})
    @TargetGradleVersion(">=3.0")
    def "can run and cancel test execution in continuous mode"() {
        given:
        collectDescriptorsFromBuild()
        and: // Need to run the test task beforehand, since continuous build doesn't handle the new directories created after 'clean'
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"))

        when:
        withConnection { connection ->
            withCancellation { cancellationToken ->
                launchTests(connection, new TestResultHandler(), cancellationToken) { TestLauncher launcher ->
                    def testsToLaunch = testDescriptors("example.MyTest", null, ":secondTest")
                    launcher
                        .withTests(testsToLaunch.toArray(new TestOperationDescriptor[testsToLaunch.size()]))
                        .withArguments("-t")
                }

                waitingForBuild()
                assertTaskExecuted(":secondTest")
                assertTaskNotExecuted(":test")
                assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
                assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
                assertTestNotExecuted(className: "example.MyTest", methodName: "foo3", task: ":secondTest")
                assertTestNotExecuted(className: "example.MyTest", methodName: "foo4", task: ":secondTest")
                assert events.tests.size() == (supportsEfficientClassFiltering() ? 5 : 6)
                events.clear()

                // Change the input to tests and wait for the tests to run again
                removeTestClass()
                waitingForBuild()
            }
        }

        then:
        assertBuildCancelled()
        assertTaskExecuted(":secondTest")
        assertTaskNotExecuted(":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        events.testTasksAndExecutors.size() in [1, 2]
        events.testClassesAndMethods.size() == (supportsEfficientClassFiltering() ? 3 : 4)
    }

    public <T> T withCancellation(@ClosureParams(value = SimpleType, options = ["org.gradle.tooling.CancellationToken"]) Closure<T> cl) {
        return cancellationTokenSource.withCancellation(cl)
    }

    def "listener errors are rethrown on client side"() {
        given:
        collectDescriptorsFromBuild()
        def descriptors = testDescriptors("example.MyTest")
        def failingProgressListener = failingProgressListener()
        when:
        withConnection { ProjectConnection connection ->
            def testLauncher = connection.newTestLauncher()
            testLauncher.addProgressListener(failingProgressListener)
            testLauncher.withTests(descriptors.toArray(new TestOperationDescriptor[descriptors.size()]))
            testLauncher.run()
        };
        then:
        def e = thrown(ListenerFailedException)
        e.cause.message == "failing progress listener"
    }

    def "fails with meaningful error when no tests declared"() {
        when:
        launchTests([])

        then:
        def e = thrown(TestExecutionException)
        e.message == "No test declared for execution."
    }

    def "build succeeds if test class is only available in one test task"() {
        given:
        file("src/moreTests/java/more/MoreTest.java") << """
            package more;
            public class MoreTest {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(2, 2);
                }
            }
        """
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("more.MoreTest")
        }
        then:
        assertTaskExecuted(":secondTest")
        assertTestExecuted(className: "more.MoreTest", methodName: "bar", task: ":secondTest")
        assertTaskExecuted(":test")
        events.tests.size() == (supportsEfficientClassFiltering() ? 5 : 10)
    }

    def "fails with meaningful error when test task no longer exists"() {
        given:
        collectDescriptorsFromBuild()
        and:
        buildFile.text = simpleJavaProject()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskNotExecuted(":secondTest")
        assertTaskNotExecuted(":test")

        def e = thrown(TestExecutionException)
        e.cause.message == "Requested test task with path ':secondTest' cannot be found."

        and:
        failure.assertHasDescription("Requested test task with path ':secondTest' cannot be found.")
        assertHasBuildFailedLogging()
    }

    def "fails with meaningful error when passing invalid arguments"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("example.MyTest")
                .withArguments("--someInvalidArgument")
        }

        then:
        def e = thrown(UnsupportedBuildArgumentException)
        e.message.contains("Unknown command-line option '--someInvalidArgument'.")
    }

    def "fails with BuildException when build fails"() {
        given:
        buildFile << "some invalid build code"
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("example.MyTest")
        }
        then:
        def e = thrown(BuildException)
        e.cause.message.contains('A problem occurred evaluating root project')

        and:
        failure.assertHasDescription('A problem occurred evaluating root project')
        assertHasBuildFailedLogging()
    }

    def "throws BuildCancelledException when build canceled before request started"() {
        given:
        buildFile << "some invalid build code"
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("example.MyTest")
            launcher.withCancellationToken(cancellationTokenSource.token())
            cancellationTokenSource.cancel()
        }
        then:
        thrown(BuildCancelledException)
    }

    def "can execute test class passed by name"() {
        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestClasses("example.MyTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        events.tests.size() == (supportsEfficientClassFiltering() ? 10 : 12)

        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
    }

    def "can execute multiple test classes passed by name"() {
        setup: "add testcase that should not be executed"
        withFailingTest()

        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestClasses("example.MyTest")
            testLauncher.withJvmTestClasses("example2.MyOtherTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
        events.tests.size() == (supportsEfficientClassFiltering() ? 14 : 16)

        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":test")
        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":secondTest")
    }

    def "runs all test tasks in multi project build when test class passed by name"() {
        setup:
        settingsFile << "include ':sub1', 'sub2', ':sub2:sub3', ':sub4'"
        ["sub1", "sub2/sub3"].each { projectFolderName ->
            file("${projectFolderName}/src/test/java/example/MyTest.java") << """
                package example;
                public class MyTest {
                    @org.junit.Test public void foo() throws Exception {
                         org.junit.Assert.assertEquals(1, 1);
                    }
                }
            """
        }

        file("sub2/src/test/java/example2/MyOtherTest.java") << """
            package example2;
            public class MyOtherTest {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
            """
        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestClasses("example.MyTest")
            testLauncher.withJvmTestClasses("example2.MyOtherTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTaskExecuted(":sub1:test")
        assertTaskExecuted(":sub2:test")
        assertTaskExecuted(":sub2:sub3:test")
        assertTaskExecuted(":sub4:test")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":sub1:test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":sub2:test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":sub2:sub3:test")
        events.tests.size() == 10 + 7 + 9
    }

    def "compatible with configure on demand"() {
        setup:
        10.times {
            settingsFile << "include ':sub$it'\n"
            file("sub$it/src/test/java/example/MyTest.java") << """
                package example;
                public class MyTest {
                    @org.junit.Test public void foo() throws Exception {
                         org.junit.Assert.assertEquals(1, 1);
                    }
                }
            """
        }
        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withArguments("--configure-on-demand")
            testLauncher.withJvmTestClasses("example.MyTest")
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":sub0:test")
        assertTaskExecuted(":sub1:test")
        assertTaskExecuted(":sub2:test")
        assertTaskExecuted(":sub3:test")
        assertTaskExecuted(":sub4:test")
        assertTaskExecuted(":sub5:test")
        assertTaskExecuted(":sub6:test")
        assertTaskExecuted(":sub7:test")
        assertTaskExecuted(":sub8:test")
        assertTaskExecuted(":sub9:test")

        and:
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":sub0:test")
    }

    ProgressListener failingProgressListener() {
        new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                throw new GradleException("failing progress listener")
            }
        }
    }

    def assertTaskOperationSuccessfulOrSkippedWithNoSource(String taskPath) {
        ProgressEvents.Operation operation = events.operation("Task $taskPath")
        if (targetVersion < GRADLE_VERSION_34) {
            assert operation.successful
        } else {
            assert operation.result instanceof TaskSkippedResult
            assert operation.result.skipMessage == "NO-SOURCE"
        }
        true
    }

    def testCode() {
        settingsFile << "rootProject.name = 'testproject'\n"
        buildFile.text = simpleJavaProject()

        def classesDir = 'file("build/classes/moreTests")'
        buildFile << """
            sourceSets {
                moreTests {
                    java.srcDir "src/test"
                    ${destinationDirectoryCode(classesDir)}
                    compileClasspath = compileClasspath + sourceSets.test.compileClasspath
                    runtimeClasspath = runtimeClasspath + sourceSets.test.runtimeClasspath
                }
            }

            task secondTest(type:Test) {
                classpath = sourceSets.moreTests.runtimeClasspath
                ${separateClassesDirs(targetVersion) ? "testClassesDirs" : "testClassesDir"} = sourceSets.moreTests.output.${separateClassesDirs(targetVersion) ? "classesDirs" : "classesDir"}
            }

            build.dependsOn secondTest
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void foo2() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        file("src/test/java/example2/MyOtherTest.java") << """
            package example2;
            public class MyOtherTest {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(2, 2);
                }
            }
        """
    }

    def removeTestClass() {
        // Removes MyTest.class to trigger a new build because it's input of test task.
        // Previously we made changes to the source files, but that might trigger
        // two builds - one is from `MyTest.java` change, another one is from
        // `MyTest.class` change. The timing of these two builds and cancellation
        // resulted in flakiness. To resolve this issue,
        // We now do a change to the class file so there
        // will be only one continuous build triggered.
        if (file("build/classes/java/test/example/MyTest.class").exists()) {
            // for Gradle 4.0+
            assert file("build/classes/java/test/example/MyTest.class").delete()
        }
        if (file("build/classes/test/example/MyTest.class").exists()) {
            // for Gradle < 4.0
            assert file("build/classes/test/example/MyTest.class").delete()
        }
    }

    String simpleJavaProject() {
        """
        allprojects{
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
        }
        """
    }

    def testClassRemoved() {
        file("src/test/java/example/MyTest.java").delete()
    }

}
