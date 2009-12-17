/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(DistributionIntegrationTestRunner.class)
public class JUnitIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void testFailureBreaksBuild() {
        TestFile testDir = dist.getTestDir();
        TestFile buildFile = testDir.file("build.gradle");
        buildFile.writelns(
                "usePlugin('java')",
                "repositories { mavenCentral() }",
                "dependencies { testCompile 'junit:junit:4.4' }"
        );
        testDir.file("src/test/java/org/gradle/BrokenTest.java").writelns(
                "package org.gradle;",
                "public class BrokenTest {",
                "@org.junit.Test public void broken() { org.junit.Assert.fail(); }",
                "}");

        ExecutionFailure failure = executer.withTasks("build").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasDescription("Execution failed for task ':test'.");
        failure.assertThatCause(startsWith("There were failing tests."));
    }

    @Test
    public void canUseTestSuperClassesFromAnotherProject() {
        TestFile testDir = dist.getTestDir();
        testDir.file("settings.gradle").write("include 'a', 'b'");
        testDir.file("b/build.gradle").writelns(
                "usePlugin('java')",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.4' }"
        );
        testDir.file("b/src/main/java/org/gradle/AbstractTest.java").writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "@org.junit.Test public void ok() { }",
                "}");
        TestFile buildFile = testDir.file("a/build.gradle");
        buildFile.writelns(
                "usePlugin('java')",
                "repositories { mavenCentral() }",
                "dependencies { testCompile project(':b') }",
                "test { options.fork() }"
        );
        testDir.file("a/src/test/java/org/gradle/SomeTest.java").writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");

        executer.withTasks("a:test").run();
        testDir.file("a/build/test-results/TEST-org.gradle.SomeTest.xml").assertIsFile();
    }

    @Test
    public void canListenForTestResults() {
        TestFile testDir = dist.getTestDir();
        testDir.file("src/main/java/AppException.java").writelns(
                "public class AppException extends Exception { }"
        );

        testDir.file("src/test/java/SomeTest.java").writelns(
                "public class SomeTest {",
                "@org.junit.Test public void pass() { }",
                "@org.junit.Test public void fail() { org.junit.Assert.fail(); }",
                "@org.junit.Test public void knownError() { throw new RuntimeException(); }",
                "@org.junit.Test public void unknownError() throws AppException { throw new AppException(); }",
                "}"
        );

        testDir.file("build.gradle").writelns(
                "usePlugin 'java'",
                "dependencies { testCompile 'junit:junit:4.4' }",
                "def listener = new TestListenerImpl()",
                "test.addTestListener(listener)",
                "test.ignoreFailures = true",
                "class TestListenerImpl implements TestListener {",
                "void suiteStarting(TestListener.Suite suite) { println 'START SUITE ' + suite.name }",
                "void suiteFinished(TestListener.Suite suite) { println 'FINISH SUITE ' + suite.name }",
                "void testStarting(TestListener.Test test) { println 'START TEST ' + test.name }",
                "void testFinished(TestListener.Test test, TestListener.Result result) { println 'FINISH TEST ' + test.name + ' error: ' + result.error }",
                "}"
        );

        ExecutionResult result = executer.withTasks("test").run();
        assertThat(result.getOutput(), containsLine("START SUITE SomeTest"));
        assertThat(result.getOutput(), containsLine("FINISH SUITE SomeTest"));
        assertThat(result.getOutput(), containsLine("START TEST pass(SomeTest)"));
        assertThat(result.getOutput(), containsLine("FINISH TEST pass(SomeTest) error: null"));
        assertThat(result.getOutput(), containsLine("START TEST fail(SomeTest)"));
        assertThat(result.getOutput(), containsLine("FINISH TEST fail(SomeTest) error: junit.framework.AssertionFailedError: "));
        assertThat(result.getOutput(), containsLine("START TEST knownError(SomeTest)"));
        assertThat(result.getOutput(), containsLine("FINISH TEST knownError(SomeTest) error: java.lang.RuntimeException"));
        assertThat(result.getOutput(), containsLine("START TEST unknownError(SomeTest)"));
        assertThat(result.getOutput(), containsLine("FINISH TEST unknownError(SomeTest) error: java.lang.RuntimeException: AppException: null"));
    }
}
