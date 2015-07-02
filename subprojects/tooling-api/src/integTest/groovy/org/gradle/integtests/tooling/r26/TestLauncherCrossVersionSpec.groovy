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

    @ToolingApiVersion(">=2.6")
    @TargetGradleVersion(">=2.6")
    def "can rerun tests by passing test descriptor"() {
        given:
        testCode()
        def allTestDescriptors = runBuildAndCollect();

        when: "passing test descriptor for test class"
        def myTestClassDescriptor = allTestDescriptors.find { it.className == "example.MyTest" && it.methodName == null }
        def testDescriptors = launchTestAndCollect(myTestClassDescriptor);
        then: "only specified test class is executed"
        testDescriptors.any { it.className == "example.MyTest" && it.methodName == "foo" }
        testDescriptors.any { it.className == "example.MyTest" && it.methodName == "foo2" }
        // test class events are still fired for non included tests (though methods are not executed)
        // TODO clarify if this is by design
        testDescriptors.any { it.className == "example2.MyOtherTest" && it.methodName == null }
        !testDescriptors.every { it.className != "example2.MyOtherTest" && it.methodName != "bar" }

        when: "passing test descriptor for test method"
        def myTestFooMethodDescriptor = allTestDescriptors.find { it.className == "example.MyTest" && it.methodName == "foo" }
        testDescriptors = launchTestAndCollect(myTestFooMethodDescriptor);
        then: "only specified test method is executed"
        testDescriptors.any { it.className == "example.MyTest" && it.methodName == "foo" }
        !testDescriptors.any { it.className == "example.MyTest" && it.methodName == "foo2" }
    }

    Set<TestOperationDescriptor> launchTestAndCollect(TestOperationDescriptor... testsToLaunch) {
        def testDescriptors = [] as Set
        withConnection { ProjectConnection connection ->
            connection.newTestLauncher()
                .withTests(testsToLaunch)
                .addProgressListener(new ProgressListener() {
                @Override
                void statusChanged(ProgressEvent event) {
                    if (event instanceof TestProgressEvent) {
                        testDescriptors << event.descriptor
                    }
                }
            }, EnumSet.of(OperationType.TEST, OperationType.TASK))
                .run()
        }
        testDescriptors

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
