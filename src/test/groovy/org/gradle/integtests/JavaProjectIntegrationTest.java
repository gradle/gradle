package org.gradle.integtests;

import static org.hamcrest.Matchers.*;
import org.junit.Test;

import java.io.IOException;

public class JavaProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesEmptyProjects() {
        testFile("build.gradle").writelns(
                "usePlugin('java')"
        );
        inTestDirectory().withTasks("libs").run();
    }

    @Test
    public void compilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('java')"
        );
        testFile("src/main/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("libs").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasContext("Execution failed for task ':compile'");
        failure.assertHasDescription("Compile failed; see the compiler error output for details.");
    }

    @Test
    public void testCompilationFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('java')"
        );
        testFile("src/main/java/org/gradle/ok.java").write("package org.gradle; class ok { }");
        testFile("src/test/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("libs").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasContext("Execution failed for task ':compileTests'");
        failure.assertHasDescription("Compile failed; see the compiler error output for details.");
    }

    @Test
    public void testFailureBreaksBuild() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('java')"
        );
        testFile("src/test/java/org/gradle/BrokenTest.java").write("package org.gradle; public class BrokenTest { }");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("libs").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasContext("Execution failed for task ':test'");
        failure.assertDescription(startsWith("There were failing tests."));
    }

    @Test
    public void javadocGenerationFailureBreaksBuild() throws IOException {
        TestFile buildFile = testFile("javadocs.gradle");
        buildFile.write("usePlugin('java')");
        testFile("src/main/java/org/gradle/broken.java").write("broken");

        ExecutionFailure failure = usingBuildFile(buildFile).withTasks("javadoc").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasContext("Execution failed for task ':javadoc'");
        failure.assertHasDescription("Javadoc generation failed.");
    }
}
