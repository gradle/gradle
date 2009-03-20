package org.gradle.integtests;

import org.junit.Test;

import java.io.IOException;

public class SettingsScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsSettingsScriptEvaluationFailsWithRuntimeException() throws IOException {
        TestFile buildFile = testFile("some build.gradle");
        TestFile settingsFile = testFile("some settings.gradle");
        settingsFile.writelns("", "", "throw new RuntimeException('<failure message>')");

        ExecutionFailure failure = usingBuildFile(buildFile).usingSettingsFile(settingsFile).withTasks("do-stuff")
                .runWithFailure();

        failure.assertHasFileName(String.format("Settings file '%s'", settingsFile));
        failure.assertHasLineNumber(3);
        failure.assertHasContext("A problem occurred evaluating the settings file.");
        failure.assertHasDescription("<failure message>");
    }
}
