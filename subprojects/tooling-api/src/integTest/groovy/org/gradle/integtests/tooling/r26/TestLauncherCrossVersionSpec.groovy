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
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
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
        givenTestDescriptors = runBuildAndCollectDescriptors();
    }


    @TargetGradleVersion(">=2.6")
    def "test launcher api fires progress events"() {
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

        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: "test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: "secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "can run specific test method passed via test descriptor"() {
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
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: "secondTest")
    }

    @TargetGradleVersion(">=2.6")
    def "passing task descriptor with unsupported task type fails with meaningful error"() {
        when:
        launchTests(taskDescriptors(":build"))
        then:
        def e = thrown(Exception)
        e.cause.message == "Task ':build' of type 'org.gradle.api.DefaultTask_Decorated' not supported for executing tests via TestLauncher API."
    }

    @TargetGradleVersion(">=2.6")
    def "runs only test task linked in test descriptor"() {
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
        when:
        withConnection {
            def cancellationTokenSource = GradleConnector.newCancellationTokenSource()

            launchTests(it, testDescriptors("example.MyTest", null, ":secondTest"), new TestResultHandler(), cancellationTokenSource,  "-t");
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

    ProgressListener failingProgressListener() {
        new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                throw new GradleException("failing progress listener")
            }
        }
    }

    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when test no longer exists"() {
        given:
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
        buildFile.text = simpleJavaProject()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":secondTest"));
        then:
        assertTaskNotExecuted(":secondTest")
        assertTaskNotExecuted(":test")

        def e = thrown(TestExecutionException)
        e.cause.message == "Requested test task with path ':secondTest' cannot be found."
    }


    def assertBuildCancelled() {
        stdout.toString().contains("Build cancelled.")
        true
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

    private void waitingForBuild() {
        ConcurrentTestUtil.poll {
            assert stdout.toString().contains("Waiting for changes to input files of tasks...");
        }
        stdout.reset()
        stderr.reset()
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
        withConnection { ProjectConnection connection ->
            launchTests(connection, testsToLaunch, null, GradleConnector.newCancellationTokenSource());
        }
    }

    def launchTests(ProjectConnection connection, Collection<TestOperationDescriptor> testsToLaunch,
                    ResultHandler<Void> resultHandler, CancellationTokenSource cancellationTokenSource, String... arguments) {
        testDescriptors.clear()
        taskEvents.clear()
        TestLauncher testLauncher = connection.newTestLauncher()
            .withTests(testsToLaunch.toArray(new OperationDescriptor[testsToLaunch.size()]))
            .withArguments(arguments)
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

        if(resultHandler == null){
            testLauncher.run()
        }else {
            testLauncher.run(resultHandler)
        }
    }

    private Set<TestOperationDescriptor> runBuildAndCollectDescriptors() {
        def allTestDescriptors = [] as Set
        try {
            withConnection {
                ProjectConnection connection ->
                    connection.newBuild().forTasks('build').withArguments("--continue").addProgressListener(new ProgressListener() {
                        @Override
                        void statusChanged(ProgressEvent event) {
                            if (event instanceof TaskFinishEvent) {
                                taskEvents << event
                            } else if (event instanceof TestProgressEvent) {
                                allTestDescriptors << event.descriptor
                            }
                        }
                    }, EnumSet.of(OperationType.TEST, OperationType.TASK)).run()
            }
        } catch (BuildException e) {
        }
        allTestDescriptors
    }

    def testCode() {
        settingsFile << "rootProject.name = 'testproject'"
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

    def simpleJavaProject() {
        """
        apply plugin: 'java'
        repositories { mavenCentral() }
        dependencies { testCompile 'junit:junit:4.12' }
        """
    }

    def testClassRemoved() {
        file("src/test/java/example/MyTest.java").delete()
    }

}
