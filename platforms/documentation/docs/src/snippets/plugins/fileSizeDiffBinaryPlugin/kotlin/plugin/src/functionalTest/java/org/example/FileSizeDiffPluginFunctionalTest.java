package org.example;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import static org.junit.jupiter.api.Assertions.*;

public class FileSizeDiffPluginFunctionalTest {
    // Temporary directory for each test, automatically cleaned up after the test run
    @TempDir
    File projectDir;

    // Helper to get reference to build.gradle in the temp project
    private File getBuildFile() {
        return new File(projectDir, "build.gradle");
    }

    // Helper to get reference to settings.gradle in the temp project
    private File getSettingsFile() {
        return new File(projectDir, "settings.gradle");
    }

    // Create minimal build and settings files before each test
    @BeforeEach
    void setup() throws IOException {
        // Empty settings.gradle
        writeString(getSettingsFile(), "");
        // Apply the plugin and configure the extension in build.gradle
        writeString(getBuildFile(),
            "plugins {\n" +
                "  id('org.example.filesizediff')\n" +
                "}\n" +
                "\n" +
                "fileSizeDiff {\n" +
                "  file1 = file('a.txt')\n" +
                "  file2 = file('b.txt')\n" +
                "}");
    }

    // Test case: both input files have the same size (empty)
    @Test
    void canDiffTwoFilesOfTheSameSize() throws IOException {
        // Create empty file a.txt
        writeString(new File(projectDir, "a.txt"), "");
        // Create empty file b.txt
        writeString(new File(projectDir, "b.txt"), "");

        // Run the build with the plugin classpath and invoke the fileSizeDiff task
        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("fileSizeDiff")
            .build();

        // Verify the output message and successful task result
        assertTrue(result.getOutput().contains("Files have the same size"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":fileSizeDiff").getOutcome());
    }

    // Test case: first file is larger than second file
    @Test
    void canDiffTwoFilesOfDiffSize() throws IOException {
        // File a.txt has 7 bytes
        writeString(new File(projectDir, "a.txt"), "dsdsdad");
        // File b.txt is empty
        writeString(new File(projectDir, "b.txt"), "");

        // Run the build and invoke the plugin task
        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("fileSizeDiff")
            .build();

        // Verify the output message indicates a.txt is larger
        assertTrue(result.getOutput().contains("a.txt was larger: 7 bytes"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":fileSizeDiff").getOutcome());
    }

    // Helper method to write string content to a file
    private void writeString(File file, String string) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            writer.write(string);
        }
    }
}
