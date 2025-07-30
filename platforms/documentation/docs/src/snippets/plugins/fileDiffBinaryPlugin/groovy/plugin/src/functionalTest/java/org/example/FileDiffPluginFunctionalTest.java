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

public class FileDiffPluginFunctionalTest {
    @TempDir
    File projectDir;

    private File getBuildFile() {
        return new File(projectDir, "build.gradle");
    }

    private File getSettingsFile() {
        return new File(projectDir, "settings.gradle");
    }

    @BeforeEach
    void setup() throws IOException {
        writeString(getSettingsFile(), "");
        writeString(getBuildFile(),
                "plugins {\n" +
                        "  id('org.example.filediff')\n" +
                        "}\n" +
                        "\n" +
                        "fileDiff {\n" +
                        "  file1 = file('a.txt')\n" +
                        "  file2 = file('b.txt')\n" +
                        "}");
    }

    @Test
    void canDiffTwoFilesOfTheSameSize() throws IOException {
        writeString(new File(projectDir, "a.txt"), "");
        writeString(new File(projectDir, "b.txt"), "");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("fileDiff")
                .build();

        assertTrue(result.getOutput().contains("Files have the same size"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":fileDiff").getOutcome());
    }

    @Test
    void canDiffTwoFilesOfDiffSize() throws IOException {
        writeString(new File(projectDir, "a.txt"), "dsdsdad");
        writeString(new File(projectDir, "b.txt"), "");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("fileDiff")
                .build();

        assertTrue(result.getOutput().contains("a.txt was larger: 7 bytes"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":fileDiff").getOutcome());
    }

    private void writeString(File file, String string) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            writer.write(string);
        }
    }
}
