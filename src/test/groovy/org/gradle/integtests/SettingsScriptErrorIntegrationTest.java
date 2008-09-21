package org.gradle.integtests;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class SettingsScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsSettingsScriptEvaluationFailsWithRuntimeException() throws IOException {
        TestFile buildFile = testFile("some build.gradle");
        TestFile settingsFile = testFile("some settings.gradle");
        settingsFile.writelns("", "", "throw new RuntimeException('<failure message>')");

        GradleExecutionFailure failure = usingBuildFile(buildFile).usingSettingsFile(settingsFile)
                .runTasksAndExpectFailure("do-stuff");

        failure.assertHasFileName(String.format("Settings file '%s'", settingsFile));
        failure.assertHasLineNumber(3);
        failure.assertHasContext("A problem occurred evaluating the settings file.");
        failure.assertHasDescription("<failure message>");
    }
}
