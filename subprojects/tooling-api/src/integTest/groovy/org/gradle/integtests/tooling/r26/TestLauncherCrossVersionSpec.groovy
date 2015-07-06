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
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestProgressEvent

@TargetGradleVersion(">=1.0-milestone-8")
class TestLauncherCrossVersionSpec extends ToolingApiSpecification {

    def currentTestDescriptors = [] as Set
    def finishedTasksEvents = [] as Set

    def givenTestDescriptors = [] as Set

    def setup() {
            testCode()
        givenTestDescriptors = runBuildAndCollectDescriptors();
    }

    @ToolingApiVersion(">=2.6")
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

    @ToolingApiVersion(">=2.6")
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

    @ToolingApiVersion(">=2.6")
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

    @ToolingApiVersion(">=2.6")
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

    @ToolingApiVersion(">=2.6")
    @TargetGradleVersion(">=2.6")
    def "fails with meaningful error when test no longer exists"() {
        given:
        testClassRemoved()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":test"));
        then:
        def e = thrown(BuildException)
        assertTaskExecuted(":test")
        assertTaskNotExecuted(":secondTest")

    }

    def testClassRemoved() {
        file("src/test/java/example/MyTest.java").delete()
    }

    @ToolingApiVersion(">=2.6")
    @TargetGradleVersion("<2.6")
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
        assert finishedTasksEvents.any{ it.descriptor.taskPath == taskPath }
        true
    }

    def assertTaskNotExecuted(String taskPath) {
        assert !finishedTasksEvents.any{ it.descriptor.taskPath == taskPath }
        true
    }

    def assertTaskUpToDate(String taskPath) {
        assert finishedTasksEvents.any{ it.descriptor.taskPath == taskPath && it.result.upToDate }
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

    Collection<TestOperationDescriptor> testDescriptors(Set<TestOperationDescriptor> descriptors = givenTestDescriptors, String className, String methodName) {
        testDescriptors(descriptors, className, methodName, null)
    }

    Collection<TestOperationDescriptor> testDescriptors(Set<TestOperationDescriptor> descriptors = givenTestDescriptors, String className) {
        testDescriptors(descriptors, className, null)
    }

    def assertTestNotExecuted(Map testInfo) {
        assert !hasTestDescriptor(testInfo)
        true
    }

    def assertTestExecuted(Map testInfo) {
        assert hasTestDescriptor(testInfo)
        true
    }

    private boolean hasTestDescriptor(testInfo) {
        !testDescriptors(currentTestDescriptors, testInfo.className, testInfo.methodName, testInfo.task).isEmpty()
    }

    void launchTests(Collection<TestOperationDescriptor> testsToLaunch) {
        currentTestDescriptors.clear()
        finishedTasksEvents.clear()
        withConnection { ProjectConnection connection ->
            connection.newTestLauncher()
                .withTests(testsToLaunch.toArray(new TestOperationDescriptor[testsToLaunch.size()]))
                .addProgressListener(new ProgressListener() {

                @Override
                void statusChanged(ProgressEvent event) {
                    if (event instanceof TaskFinishEvent) {
                        finishedTasksEvents << event
                    } else if (event instanceof TestProgressEvent) {
                        currentTestDescriptors << event.descriptor
                    }
                }
            }, EnumSet.of(OperationType.TEST, OperationType.TASK))
            .run()
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
                            if (event instanceof TestProgressEvent) {
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
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }

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
}
