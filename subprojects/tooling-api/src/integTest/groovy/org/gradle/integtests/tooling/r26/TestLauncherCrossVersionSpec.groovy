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
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestProgressEvent

class TestLauncherCrossVersionSpec extends ToolingApiSpecification {

    def currentTestDescriptors = [] as Set

    @ToolingApiVersion(">=2.6")
    @TargetGradleVersion(">=2.6")
    def "can rerun tests by passing test descriptor"() {
        given:
        testCode()
        def allTestDescriptors = runBuildAndCollect();

        when: "passing test descriptor for test class"
        def myTestClassDescriptors = findDescriptors(allTestDescriptors, "example.MyTest", null)
        launchTests(myTestClassDescriptors);
        then: "only specified test class is executed"
        assertTestExecuted(className:"example.MyTest", methodName:"foo")
        assertTestExecuted(className:"example.MyTest", methodName:"foo2")
        assertTestExecuted(className:"example2.MyOtherTest", methodName:null) // TODO clarify if this is by design
        assertTestNotExecuted(className:"example2.MyOtherTest", methodName:"bar")

        when: "passing test descriptor for test method"
        def myTestFooMethodDescriptor = findDescriptors(allTestDescriptors, "example.MyTest", "foo")
        launchTests(myTestFooMethodDescriptor);
        then: "only specified test method is executed"
        assertTestExecuted(className:"example.MyTest", methodName:"foo")
        assertTestNotExecuted(className:"example.MyTest", methodName:"foo2")
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

    Collection<TestOperationDescriptor> findDescriptors(Set<TestOperationDescriptor> descriptors, String className, String methodName) {
        descriptors.findAll { it.className == className && it.methodName == methodName }
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
        currentTestDescriptors.any { it.className == testInfo.className && it.methodName == testInfo.methodName }
    }

    void launchTests(Collection<TestOperationDescriptor> testsToLaunch) {
        currentTestDescriptors.clear()
        withConnection { ProjectConnection connection ->
            connection.newTestLauncher()
                .withTests(testsToLaunch.toArray(new TestOperationDescriptor[testsToLaunch.size()]))
                .addProgressListener(new ProgressListener() {
                @Override
                void statusChanged(ProgressEvent event) {
                    if (event instanceof TestProgressEvent) {
                        currentTestDescriptors << event.descriptor
                    }
                }
            }, EnumSet.of(OperationType.TEST, OperationType.TASK))
                .run()
        }
    }

    private Set<TestOperationDescriptor> runBuildAndCollect() {
        def allTestDescriptors = [] as Set
        try {
            withConnection {
                ProjectConnection connection ->
                    connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
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
                     org.junit.Assert.fail();
                }
            }
        """
    }
}
