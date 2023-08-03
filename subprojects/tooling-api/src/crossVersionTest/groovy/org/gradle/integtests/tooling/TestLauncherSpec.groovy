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

import groovy.transform.CompileStatic
import org.gradle.integtests.tooling.fixture.GradleBuildCancellation
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.BuildException
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.util.GradleVersion
import org.junit.Rule

import static org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification.getWaitingMessage

abstract class TestLauncherSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    ProgressEvents events = ProgressEvents.create()

    @Rule
    GradleBuildCancellation cancellationTokenSource

    def setup() {
        // Avoid mixing JUnit dependencies with the ones from the JVM running this test
        // For example, when using PTS/TD for running this test, the JUnit Platform Launcher classes from the GE plugin take precedence
        toolingApi.requireDaemons()
        testCode()
    }

    boolean supportsEfficientClassFiltering() {
        return getTargetVersion() >= GradleVersion.version('4.7')
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
            .addProgressListener(events, OperationType.TASK, OperationType.TEST)

        collectOutputs(testLauncher)

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
        ConcurrentTestUtil.poll(30) {
            assert stdout.toString().contains(getWaitingMessage(targetVersion))
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

    private static boolean matchIfPresent(String actual, String requested) {
        if (requested == null) {
            return true
        }
        actual == requested
    }

    Collection<TestOperationDescriptor> testDescriptors(String className, String methodName = null, String taskPath = null, String displayName = null) {
        findTestDescriptors(events.tests.collect { it.descriptor }, className, methodName, taskPath, displayName)
    }

    private static Collection<TestOperationDescriptor> findTestDescriptors(List<TestOperationDescriptor> descriptors, String className, String methodName = null, String taskpath = null, String displayName = null) {

        def descriptorByClassAndMethod = descriptors.findAll {
            it.className == className &&
                it.methodName == methodName &&
                matchIfPresent(it.displayName, displayName)
        }
        if (taskpath == null) {
            return descriptorByClassAndMethod
        }

        return descriptorByClassAndMethod.findAll {
            def parent = it
            while (parent.parent != null) {
                parent = parent.parent
                if (parent instanceof TaskOperationDescriptor) {
                    return parent.taskPath == taskpath
                }
            }
            false
        }
    }

    boolean hasTestDescriptor(testInfo) {
        def collect = events.tests.collect { it.descriptor }
        !findTestDescriptors(collect, testInfo.className, testInfo.methodName, testInfo.task, testInfo.displayName).isEmpty()
    }


    void collectDescriptorsFromBuild() {
        try {
            withConnection {
                ProjectConnection connection ->
                    connection.newBuild().forTasks('build')
                        .withArguments("--continue")
                        .addProgressListener(events)
                        .setStandardOutput(System.out)
                        .setStandardError(System.err)
                        .run()
            }
        } catch (BuildException e) {
        }
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
        addDefaultTests()
    }

    void addDefaultTests() {
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

    static boolean separateClassesDirs(GradleVersion version) {
        version.baseVersion >= GradleVersion.version("4.0")
    }

    String destinationDirectoryCode(String destinationDirectory) {
        //${separateClassesDirs(targetVersion) ? "java.outputDir" : "output.classesDir"} = file("build/classes/moreTests")
        if (!separateClassesDirs(targetVersion)) {
            return "output.classesDir = $destinationDirectory"
        }
        if (targetVersion.baseVersion < GradleVersion.version("6.1")) {
            return "java.outputDir = $destinationDirectory"
        }
        return "java.destinationDirectory.set($destinationDirectory)"
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

    String simpleJavaProject() {
        """
        allprojects{
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
        }
        """
    }

    void jvmTestEvents(@DelegatesTo(value = TestEventsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        DefaultTestEventsSpec spec = new DefaultTestEventsSpec()
        assertionSpec.delegate = spec
        assertionSpec.resolveStrategy = Closure.DELEGATE_FIRST
        assertionSpec()
        def remainingEvents = spec.testEvents - spec.verifiedEvents
        if (remainingEvents) {
            ErrorMessageBuilder err = new ErrorMessageBuilder()
            err.title("The following test events were received but not verified")
            remainingEvents.each { err.candidate("${it} : Kind=${it.jvmTestKind} suiteName=${it.suiteName} className=${it.className} methodName=${it.methodName} displayName=${it.displayName}") }
            throw err.build()
        }
    }

    interface TestEventsSpec {
        void task(String path, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec)
    }

    interface TestEventSpec {
        void displayName(String displayName)

        void suite(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)

        void testClass(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)

        void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)
    }

    class DefaultTestEventsSpec implements TestEventsSpec {
        final List<JvmTestOperationDescriptor> testEvents = events.tests.collect { (JvmTestOperationDescriptor) it.descriptor }
        final Set<OperationDescriptor> verifiedEvents = []

        @Override
        void task(String path, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec) {
            def task = testEvents.find {
                it.jvmTestKind == JvmTestKind.SUITE &&
                    (it.parent instanceof TaskOperationDescriptor) &&
                    it.parent.taskPath == path
            }
            if (task == null) {
                throw new AssertionError("Expected to find a test task $path but none was found")
            }
            DefaultTestEventSpec.assertSpec(task.parent, testEvents, verifiedEvents, rootSpec)
        }
    }

    @CompileStatic
    static class DefaultTestEventSpec implements TestEventSpec {
        private final List<JvmTestOperationDescriptor> testEvents
        private final Set<OperationDescriptor> verifiedEvents
        private final OperationDescriptor parent
        private String displayName

        static void assertSpec(OperationDescriptor descriptor, List<JvmTestOperationDescriptor> testEvents, Set<OperationDescriptor> verifiedEvents, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            verifiedEvents.add(descriptor)
            DefaultTestEventSpec childSpec = new DefaultTestEventSpec(descriptor, testEvents, verifiedEvents)
            spec.delegate = childSpec
            spec.resolveStrategy = Closure.DELEGATE_FIRST
            spec()
            childSpec.validate()
        }

        DefaultTestEventSpec(OperationDescriptor parent, List<JvmTestOperationDescriptor> testEvents, Set<OperationDescriptor> verifiedEvents) {
            this.parent = parent
            this.testEvents = testEvents
            this.verifiedEvents = verifiedEvents
        }

        @Override
        void displayName(String displayName) {
            this.displayName = displayName
        }

        private static String normalizeExecutor(String name) {
            if (name.startsWith("Gradle Test Executor")) {
                return "Gradle Test Executor"
            }
            return name
        }

        @Override
        void suite(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            def child = testEvents.find { it.parent == parent && it.jvmTestKind == JvmTestKind.SUITE && normalizeExecutor(it.suiteName) == name }
            if (child == null) {
                failWith("test suite", name)
            }
            assertSpec(child, testEvents, verifiedEvents, spec)
        }

        @Override
        void testClass(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            def child = testEvents.find {
                it.parent == parent &&
                    it.jvmTestKind == JvmTestKind.SUITE &&
                    it.suiteName == null &&
                    it.className == name
            }
            if (child == null) {
                failWith("test class", name)
            }
            assertSpec(child, testEvents, verifiedEvents, spec)
        }

        @Override
        void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            def child = testEvents.find {
                it.parent == parent &&
                    it.jvmTestKind == JvmTestKind.ATOMIC &&
                    it.suiteName == null &&
                    it.name == name
            }
            if (child == null) {
                failWith("test", name)
            }
            assertSpec(child, testEvents, verifiedEvents, spec)
        }

        private void failWith(String what, String name) {
            ErrorMessageBuilder err = new ErrorMessageBuilder()
            def remaining = testEvents.findAll { it.parent == parent && !verifiedEvents.contains(it) }
            if (remaining) {
                err.title("Expected to find a $what named $name under ${parent.displayName} and none was found. Possible events are:")
                remaining.each {
                    err.candidate("${it} : Kind=${it.jvmTestKind} suiteName=${it.suiteName} className=${it.className} methodName=${it.methodName} displayName=${it.displayName}")
                }
            } else {
                err.title("Expected to find a $what named $name under ${parent.displayName} and none was found. There are no more events available for this parent.")
            }
            throw err.build()
        }

        void validate() {
            if (displayName != null) {
                assert displayName == parent.displayName
            }
        }
    }

    @CompileStatic
    static class ErrorMessageBuilder {
        private final StringBuilder builder = new StringBuilder()
        boolean inCandidates = false


        void title(String title) {
            builder.append(title)
        }

        void candidate(String candidate) {
            if (!inCandidates) {
                builder.append(":\n")
            }
            inCandidates = true
            builder.append("   - ").append(candidate).append("\n")
        }

        AssertionError build() {
            new AssertionError(builder)
        }

    }
}
