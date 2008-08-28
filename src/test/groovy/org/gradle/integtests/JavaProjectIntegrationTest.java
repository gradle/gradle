package org.gradle.integtests;

import org.gradle.Build;
import org.gradle.StartParameter;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.gradle.api.GradleException;
import static org.junit.Assert.*;
import org.junit.Test;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;

public class JavaProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void javadocGenerationFailsOnError() throws IOException {
        File buildFile = new File(getTestDir(), "javadocs.gradle");
        FileUtils.writeStringToFile(buildFile, "usePlugin(org.gradle.api.plugins.JavaPlugin)");
        FileUtils.writeStringToFile(new File(getTestDir(), "src/main/java/org/gradle/broken.java"), "broken");

        GradleExecutionFailure failure = usingBuildFile(buildFile).runTasksAndExpectFailure("javadoc");
        
        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasDescription("Javadoc generation failed.");
    }
}
