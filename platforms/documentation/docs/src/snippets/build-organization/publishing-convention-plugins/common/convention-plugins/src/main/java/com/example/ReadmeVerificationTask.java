package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * Verifies that the given readme file contains the desired patterns.
 */
public abstract class ReadmeVerificationTask extends DefaultTask {

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    public abstract RegularFileProperty getReadme();

    @Internal
    public abstract ListProperty<String> getReadmePatterns();

    @TaskAction
    void verifyServiceReadme() throws IOException {
        String readmeContents = new String (Files.readAllBytes(getReadme().getAsFile().get().toPath()));
        for (String requiredSection : getReadmePatterns().get()) {
            Pattern pattern = Pattern.compile(requiredSection, Pattern.MULTILINE);
            if (!pattern.matcher(readmeContents).find()) {
                throw new RuntimeException("README should contain section: " + pattern.pattern());
            }
        }
    }
}
