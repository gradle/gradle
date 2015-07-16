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
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.integtests.tooling.fixture.*
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.*
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestProgressEvent
import org.gradle.tooling.test.TestExecutionException
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@ToolingApiVersion(">=2.6")
@TargetGradleVersion(">=1.0-milestone-8")
class TestLauncherCrossVersionSpec extends ToolingApiSpecification {
    TestOutputStream stderr = new TestOutputStream()
    TestOutputStream stdout = new TestOutputStream()

    def testDescriptors = [] as Set
    def taskEvents = [] as Set

    def givenTestDescriptors = [] as Set

    def setup() {
        testCode()
    }

    @TargetGradleVersion(">=2.6")
    def "test launcher api fires progress events"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(testDescriptors("example.MyTest"));
        then:
        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":compileJava" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":compileJava" }
        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":processResources" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":processResources" }
        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":classes" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":classes" }
        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":compileTestJava" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":compileTestJava" }
        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":compileTestJava" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":compileTestJava" }

        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":processTestResources" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":processTestResources" }

        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":testClasses" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":testClasses" }
        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":test" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":test" }

        taskEvents.any { it instanceof TaskStartEvent && it.descriptor.taskPath == ":secondTest" }
        taskEvents.any { it instanceof TaskFinishEvent && it.descriptor.taskPath == ":secondTest" }

        testDescriptors.any { it instanceof JvmTestOperationDescriptor && it.name == "Gradle Test Run :test" }
        testDescriptors.any { it instanceof JvmTestOperationDescriptor && it.name == "Gradle Test Executor 1" }
        testDescriptors.any { it instanceof JvmTestOperationDescriptor && it.name == "Gradle Test Run :secondTest" }
        testDescriptors.any { it instanceof JvmTestOperationDescriptor && it.name == "Gradle Test Executor 2" }
        testDescriptors.findAll { it instanceof JvmTestOperationDescriptor && it.displayName == "Test class example.MyTest" }.size() == 2
        testDescriptors.findAll { it instanceof JvmTestOperationDescriptor && it.displayName == "Test foo(example.MyTest)" }.size() == 2
        testDescriptors.findAll { it instanceof JvmTestOperationDescriptor && it.displayName == "Test foo2(example.MyTest)" }.size() == 2
        testDescriptors.findAll { it instanceof JvmTestOperationDescriptor && it.displayName == "Test foo2(example.MyTest)" }.size() == 2
        testDescriptors.findAll { it instanceof JvmTestOperationDescriptor && it.displayName == "Test class example2.MyOtherTest" }.size() == 2
    }

    @TargetGradleVersion(">=2.6")
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
        assertTestExecuted(className: "example2.MyOtherTest", methodName: null) // TODO clarify if this is by design

        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
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

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }

    @TargetGradleVersion(">=2.6")
    def "runs all tests when test task descriptor is passed"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(taskDescriptors(":test") + testDescriptors("example.MyTest", "foo", ":test"));
        then:
        assertTaskExecuted(":test")
        assertTaskNotExecuted(":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "passing task descriptor with unsupported task type fails with meaningful error"() {
        given:
        collectDescriptorsFromBuild()
        when:
        launchTests(taskDescriptors(":build"))
        then:
        def e = thrown(Exception)
        e.cause.message == "Task ':build' of type 'org.gradle.api.DefaultTask_Decorated' not supported for executing tests via TestLauncher API."
    }

    @TargetGradleVersion(">=2.6")
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

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }

    @TargetGradleVersion(">=2.6")
    def "test task up-to-date when launched with same test descriptors again"() {
        given:
        collectDescriptorsFromBuild()
        and:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"))
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskExecuted(":secondTest")
        assertTaskUpToDate(":secondTest")
        assertTaskNotExecuted(":test")
    }

    @TargetGradleVersion(">=2.6")
    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "can run and cancel testlauncher in continuous mode"() {
        given:
        collectDescriptorsFromBuild()
        when:
        withConnection {
            def cancellationTokenSource = GradleConnector.newCancellationTokenSource()
            launchTests(it, new TestResultHandler(), cancellationTokenSource) { TestLauncher launcher ->
                def testsToLaunch = testDescriptors("example.MyTest", null, ":secondTest")
                launcher
                    .withTests(testsToLaunch.toArray(new OperationDescriptor[testsToLaunch.size()]))
                    .withArguments("-t")
            }
            waitingForBuild()
            assertTaskExecuted(":secondTest")
            assertTaskNotExecuted(":test")
            assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
            assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
            assertTestNotExecuted(className: "example.MyTest", methodName: "foo3", task: ":secondTest")
            assertTestNotExecuted(className: "example.MyTest", methodName: "foo4", task: ":secondTest")

            testDescriptors.clear()
            changeTestSource()
            waitingForBuild()

            cancellationTokenSource.cancel()
        }

        then:
        assertBuildCancelled()
        assertTaskExecuted(":secondTest")
        assertTaskNotExecuted(":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo3", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo4", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "listener errors are rethrown on client side"() {
        given:
        collectDescriptorsFromBuild()
        def taskDescriptors = taskDescriptors(":test")
        def failingProgressListener = failingProgressListener()
        when:
        withConnection { ProjectConnection connection ->
            def testLauncher = connection.newTestLauncher()
            testLauncher.addProgressListener(failingProgressListener)
            testLauncher.withTests(taskDescriptors.toArray(new TaskOperationDescriptor[taskDescriptors.size()]))
            testLauncher.run()
        };
        then:
        def e = thrown(ListenerFailedException)
        e.cause.message == "failing progress listener"
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when test no longer exists"() {
        given:
        collectDescriptorsFromBuild()
        and:
        testClassRemoved()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":test"));
        then:
        assertTaskExecuted(":test")
        assertTaskNotExecuted(":secondTest")

        def e = thrown(TestExecutionException)
        e.cause.message == "No tests found for given includes: [example.MyTest.*]"
    }

    @TargetGradleVersion(">=2.6")
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
    }

    @TargetGradleVersion(">=1.0-milestone-8 <2.6")
    def "fails with meaningful error when running against unsupported target version"() {
        when:
        withConnection { ProjectConnection connection ->
            connection.newTestLauncher().run()
        }

        then:
        def e = thrown(UnsupportedVersionException)
        e.message == "TestLauncher API not supported by Gradle provider version"
    }

    @TargetGradleVersion(">=2.6")
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

        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "can execute multiple test classes passed by name"() {
        setup: "add testcase that should not be exeucted"
        file("src/test/java/example/MyFailingTest.java") << """
            package example;
            public class MyFailingTest {
                @org.junit.Test public void failing1() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
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

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")

        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "failing1", task: ":test")
        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "failing1", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
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
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "compatible with configure on demand"() {
        setup:
        10.times{
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
    }

    ProgressListener failingProgressListener() {
        new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                throw new GradleException("failing progress listener")
            }
        }
    }


    def assertBuildCancelled() {
        stdout.toString().contains("Build cancelled.")
        true
    }

    private void waitingForBuild() {
        ConcurrentTestUtil.poll {
            assert stdout.toString().contains("Waiting for changes to input files of tasks...");
        }
        stdout.reset()
        stderr.reset()
    }

    boolean assertTaskExecuted(String taskPath) {
        assert taskEvents.findAll { it instanceof TaskFinishEvent }.any { it.descriptor.taskPath == taskPath }
        true
    }

    def assertTaskNotExecuted(String taskPath) {
        assert !taskEvents.findAll { it instanceof TaskFinishEvent }.any { it.descriptor.taskPath == taskPath }
        true
    }

    def assertTaskUpToDate(String taskPath) {
        assert taskEvents.findAll { it instanceof TaskFinishEvent }.any { it.descriptor.taskPath == taskPath && it.result.upToDate }
        true
    }

    def assertTestNotExecuted(Map testInfo) {
        assert !hasTestDescriptor(testInfo)
        true
    }

    def assertTestExecuted(Map testInfo) {
        assert hasTestDescriptor(testInfo)
        true
    }

    Collection<TestOperationDescriptor> testDescriptors(Set<TestOperationDescriptor> descriptors = givenTestDescriptors, String className, String methodName, String taskpath) {

        def descriptorByClassAndMethod = descriptors.findAll { it.className == className && it.methodName == methodName }
        if (taskpath == null) {
            return descriptorByClassAndMethod
        }

        return descriptorByClassAndMethod.findAll {
            def parent = it.parent
            while (parent.parent != null) {
                parent = parent.parent
            }
            if (parent instanceof TaskOperationDescriptor) {
                return parent.taskPath == taskpath
            }
            false
        }
    }

    Collection<OperationDescriptor> taskDescriptors(Set<TaskFinishEvent> taskEvents = this.taskEvents, String taskPath) {
        taskEvents.findAll { it instanceof TaskFinishEvent }.collect { it.descriptor }.findAll { it.taskPath == taskPath }
    }

    Collection<TestOperationDescriptor> testDescriptors(Set<TestOperationDescriptor> descriptors = givenTestDescriptors, String className, String methodName) {
        testDescriptors(descriptors, className, methodName, null)
    }

    Collection<TestOperationDescriptor> testDescriptors(Set<TestOperationDescriptor> descriptors = givenTestDescriptors, String className) {
        testDescriptors(descriptors, className, null)
    }


    private boolean hasTestDescriptor(testInfo) {
        !testDescriptors(testDescriptors, testInfo.className, testInfo.methodName, testInfo.task).isEmpty()
    }

    void launchTests(Collection<OperationDescriptor> testsToLaunch) {
        launchTests { TestLauncher testLauncher ->
            testLauncher.withTests(testsToLaunch.toArray(new OperationDescriptor[testsToLaunch.size()]))
        }
    }

    void launchTests(Closure configurationClosure) {
        withConnection { ProjectConnection connection ->
            launchTests(connection, null, GradleConnector.newCancellationTokenSource(), configurationClosure)
        }
    }

    def launchTests(ProjectConnection connection, ResultHandler<Void> resultHandler, CancellationTokenSource cancellationTokenSource, Closure confgurationClosure) {
        testDescriptors.clear()
        taskEvents.clear()
        TestLauncher testLauncher = connection.newTestLauncher()
            .withCancellationToken(cancellationTokenSource.token())
            .addProgressListener(new ProgressListener() {

            @Override
            void statusChanged(ProgressEvent event) {
                if (event instanceof TaskProgressEvent) {
                    taskEvents << event
                } else if (event instanceof TestProgressEvent) {
                    testDescriptors << event.descriptor
                }
            }
        }, EnumSet.of(OperationType.TEST, OperationType.TASK))

        if (toolingApi.isEmbedded()) {
            testLauncher
                .setStandardOutput(stdout)
                .setStandardError(stderr)
        } else {
            testLauncher
                .setStandardOutput(new TeeOutputStream(stdout, System.out))
                .setStandardError(new TeeOutputStream(stderr, System.err))
        }

        confgurationClosure.call(testLauncher)

        if (resultHandler == null) {
            testLauncher.run()
        } else {
            testLauncher.run(resultHandler)
        }
    }

    private collectDescriptorsFromBuild() {
        givenTestDescriptors = [] as Set
        try {
            withConnection {
                ProjectConnection connection ->
                    connection.newBuild().forTasks('build').withArguments("--continue").addProgressListener(new ProgressListener() {
                        @Override
                        void statusChanged(ProgressEvent event) {
                            if (event instanceof TaskFinishEvent) {
                                taskEvents << event
                            } else if (event instanceof TestProgressEvent) {
                                givenTestDescriptors << event.descriptor
                            }
                        }
                    }, EnumSet.of(OperationType.TEST, OperationType.TASK)).run()
            }
        } catch (BuildException e) {
        }
        givenTestDescriptors
    }

    def testCode() {
        settingsFile << "rootProject.name = 'testproject'\n"
        buildFile.text = simpleJavaProject()

        buildFile << """
            task secondTest(type:Test) {
                classpath = sourceSets.test.runtimeClasspath
                testClassesDir = sourceSets.test.output.classesDir
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


    def changeTestSource() {
        // adding two more test methods
        file("src/test/java/example/MyTest.java").text = """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void foo2() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void foo3() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test public void foo4() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
    }


    def simpleJavaProject() {
        """
        allprojects{
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
        }
        """
    }

    def testClassRemoved() {
        file("src/test/java/example/MyTest.java").delete()
    }

}
