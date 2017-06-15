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

package org.gradle.integtests.tooling

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.tooling.fixture.GradleBuildCancellation
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.BuildException
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.junit.Rule

abstract class TestLauncherSpec extends ToolingApiSpecification {
    TestOutputStream stderr = new TestOutputStream()
    TestOutputStream stdout = new TestOutputStream()

    ProgressEvents events = ProgressEvents.create()

    @Rule
    GradleBuildCancellation cancellationTokenSource

    def setup() {
        testCode()
    }

    void launchTests(Collection<TestOperationDescriptor> testsToLaunch) {
        launchTests { TestLauncher testLauncher ->
            testLauncher.withTests(testsToLaunch)
        }
    }

    void launchTests(Closure configurationClosure) {
        withConnection { ProjectConnection connection ->
            launchTests(connection, null, cancellationTokenSource.token(), configurationClosure)
        }
    }

    void launchTests(ProjectConnection connection, ResultHandler<Void> resultHandler, CancellationToken cancellationToken, Closure configurationClosure) {
        TestLauncher testLauncher = connection.newTestLauncher()
            .withCancellationToken(cancellationToken)
            .addProgressListener(events)

        if (toolingApi.isEmbedded()) {
            testLauncher
                .setStandardOutput(stdout)
                .setStandardError(stderr)
        } else {
            testLauncher
                .setStandardOutput(new TeeOutputStream(stdout, System.out))
                .setStandardError(new TeeOutputStream(stderr, System.err))
        }

        configurationClosure.call(testLauncher)

        events.clear()
        if (resultHandler == null) {
            testLauncher.run()
        } else {
            testLauncher.run(resultHandler)
        }
    }

    def assertBuildCancelled() {
        stdout.toString().contains("Build cancelled.")
        true
    }

    void waitingForBuild() {
        ConcurrentTestUtil.poll {
            assert stdout.toString().contains("Waiting for changes to input files of tasks...");
        }
        stdout.reset()
        stderr.reset()
    }

    boolean assertTaskExecuted(String taskPath) {
        assert events.all.findAll { it instanceof TaskFinishEvent }.any { it.descriptor.taskPath == taskPath }
        true
    }

    def assertTaskNotExecuted(String taskPath) {
        assert !events.all.findAll { it instanceof TaskFinishEvent }.any { it.descriptor.taskPath == taskPath }
        true
    }

    def assertTaskNotUpToDate(String taskPath) {
        assert events.all.findAll { it instanceof TaskFinishEvent }.any { it.descriptor.taskPath == taskPath && !it.result.upToDate }
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

    Collection<TestOperationDescriptor> testDescriptors(List<TestOperationDescriptor> descriptors = events.tests.collect { it.descriptor }, String className, String methodName, String taskpath) {

        def descriptorByClassAndMethod = descriptors.findAll { it.className == className && it.methodName == methodName }
        if (taskpath == null) {
            return descriptorByClassAndMethod
        }

        return descriptorByClassAndMethod.findAll {
            def parent = it.parent
            while (parent.parent != null) {
                parent = parent.parent
                if (parent instanceof TaskOperationDescriptor) {
                    return parent.taskPath == taskpath
                }
            }
            false
        }
    }

    Collection<TestOperationDescriptor> testDescriptors(List<TestOperationDescriptor> descriptors = events.tests.collect { it.descriptor }, String className, String methodName) {
        testDescriptors(descriptors, className, methodName, null)
    }

    Collection<TestOperationDescriptor> testDescriptors(List<TestOperationDescriptor> descriptors = events.tests.collect { it.descriptor }, String className) {
        testDescriptors(descriptors, className, null)
    }

    boolean hasTestDescriptor(testInfo) {
        def collect = events.tests.collect { it.descriptor }
        !testDescriptors(collect, testInfo.className, testInfo.methodName, testInfo.task).isEmpty()
    }


    void collectDescriptorsFromBuild() {
        try {
            withConnection {
                ProjectConnection connection ->
                    connection.newBuild().forTasks('build').withArguments("--continue").addProgressListener(events).run()
            }
        } catch (BuildException e) {
        }
    }

    def testCode() {
        settingsFile << "rootProject.name = 'testproject'\n"
        buildFile.text = simpleJavaProject()

        buildFile << """
            sourceSets {
                moreTests {
                    java.srcDir "src/test"
                    output.classesDir = file("build/classes/moreTests")
                    compileClasspath = compileClasspath + sourceSets.test.compileClasspath
                    runtimeClasspath = runtimeClasspath + sourceSets.test.runtimeClasspath
                }
            }

            task secondTest(type:Test) {
                classpath = sourceSets.moreTests.runtimeClasspath
                testClassesDir = sourceSets.moreTests.output.classesDir
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
        file("src/test/java/example2/MyOtherTest2.java") << """
            package example2;
            public class MyOtherTest2 {
                @org.junit.Test public void baz() throws Exception {
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

    def withFailingTest() {
        file("src/test/java/example/MyFailingTest.java").text = """
            package example;
            public class MyFailingTest {
                @org.junit.Test public void fail() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
                }

                @org.junit.Test public void fail2() throws Exception {
                     org.junit.Assert.assertEquals(1, 2);
                }
            }"""

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

}
