package org.example;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MyPluginFunctionalTest {
    @TempDir File testProjectDir;
    private File settingsFile;
    private File buildFile;
    private File cacheDir = new File("build-cache");

    @BeforeEach
    public void setup() throws IOException {
        settingsFile = new File(testProjectDir, "settings.gradle");
        Files.writeString(settingsFile.toPath(), "rootProject.name = 'test-project'");

        buildFile = new File(testProjectDir, "build.gradle");
    }

    @Test
    public void testTaskRegistration() throws IOException { // <1>
        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .forwardOutput()
            .withArguments("tasks", "--all")
            .build();

        assertContainsIgnoringEol("""
            Custom Tasks tasks
            ------------------
            task1
            task2
            """,
            result.getOutput()
        );
    }

    @Test
    public void testTaskExecution() throws IOException { // <2>
        File outputFile = new File(testProjectDir, "build/output.txt");

        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }

            myExtension {
                firstName = "John"
                lastName = "Smith"
            }

            tasks.task1 {
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("task1")
            .build();

        String actual = Files.readString(outputFile.toPath());
        assertTrue(actual.startsWith("Hi, John Smith, it's currently"));
    }

    @Test
    public void testTaskDeterminism() throws IOException { // <3>
        File outputFile = new File(testProjectDir, "build/output.txt");

        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }

            myExtension {
                firstName = "John"
                lastName = "Smith"
            }

            tasks.task1 {
                outputFile = project.layout.buildDirectory.file("output.txt")
                today = ZonedDateTime.parse("2026-01-12T16:00:00-05:00").toInstant()
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        GradleRunner runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath();

        runner.withArguments("task1").build();
        String output1 = Files.readString(outputFile.toPath());

        runner.withArguments("task1", "--rerun-tasks").build();
        String output2 = Files.readString(outputFile.toPath());

        assertEquals(output1, output2);
    }

    @Test
    public void testTaskCacheability() throws IOException { // <4>
        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }

            myExtension {
                firstName = "John"
                lastName = "Smith"
            }

            tasks.task1 {
                outputFile = project.layout.buildDirectory.file("output.txt")
                today = ZonedDateTime.parse("2026-01-12T16:00:00-05:00").toInstant()
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        GradleRunner runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .forwardOutput();

        BuildResult result = runner.withArguments("--build-cache", "task1").build();
        assertEquals(SUCCESS, result.task(":task1").getOutcome());

        FileUtils.deleteDirectory(new File(testProjectDir, "build"));

        result = runner.withArguments("--build-cache", "task1").build();
        assertEquals(FROM_CACHE, result.task(":task1").getOutcome());
    }

    private static void assertContainsIgnoringEol(String expected, String actual) {
        assertTrue(normalizeEol(actual).contains(normalizeEol(expected)));
    }

    private static String normalizeEol(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }
}
