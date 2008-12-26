package org.gradle.integtests;

import org.junit.Test;

import java.io.IOException;

public class JavaProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void javadocGenerationFailsOnError() throws IOException {
        TestFile buildFile = testFile("javadocs.gradle");
        buildFile.write("usePlugin(org.gradle.api.plugins.JavaPlugin)");
        testFile("src/main/java/org/gradle/broken.java").write("broken");

        GradleExecutionFailure failure = usingBuildFile(buildFile).runTasksAndExpectFailure("javadoc");

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasContext("Execution failed for task ':javadoc'");
        failure.assertHasDescription("Javadoc generation failed.");
    }
}
