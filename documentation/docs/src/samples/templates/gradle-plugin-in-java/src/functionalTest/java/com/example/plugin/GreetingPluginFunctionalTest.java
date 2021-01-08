package com.example.plugin;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;


public class GreetingPluginFunctionalTest {
    @Test
    public void canRunTask() throws IOException {
        // Setup the test build
        File projectDir = new File("build/functionalTest");
        Files.createDirectories(projectDir.toPath());
        writeString(new File(projectDir, "settings.gradle"), "");
        writeString(new File(projectDir, "build.gradle"),
            "plugins {" +
                "  id('com.example.plugin.greeting')" +
                "}");

        // Run the build
        BuildResult result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("greet")
            .withProjectDir(projectDir)
            .build();

        // Verify the result
        assertTrue(result.getOutput().contains("Hello from plugin 'com.example.plugin.greeting'"));
    }

    private void writeString(File file, String string) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            writer.write(string);
        }
    }
}
