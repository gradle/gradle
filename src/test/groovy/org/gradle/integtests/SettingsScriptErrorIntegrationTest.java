package org.gradle.integtests;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class SettingsScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsSettingsScriptEvaluationFailsWithRuntimeException() throws IOException {
        File buildFile = new File(getTestDir(), "some build.gradle");
        File settingsFile = new File(getTestDir(), "some settings.gradle");
        FileUtils.writeStringToFile(settingsFile, "\n\nthrow new RuntimeException('<failure message>')");

        GradleExecutionFailure failure = usingBuildFile(buildFile).usingSettingsFile(settingsFile).runTasksAndExpectFailure("do-stuff");

        failure.assertHasFileName(String.format("Settings file '%s'", settingsFile));
        failure.assertHasLineNumber(3);
        failure.assertHasDescription("<failure message>");
    }
}
