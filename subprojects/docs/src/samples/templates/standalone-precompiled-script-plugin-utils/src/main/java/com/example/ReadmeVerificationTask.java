package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GFileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

public abstract class ReadmeVerificationTask extends DefaultTask {

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    public abstract RegularFileProperty getReadme();

    @Internal
    public abstract ListProperty<String> getReadmePatterns();

    @TaskAction
    void verifyServiceReadme() throws IOException {
        var readmeContents = Files.readString(getReadme().getAsFile().get().toPath());
        for (String requiredSection : getReadmePatterns().get()) {
            var pattern = Pattern.compile(requiredSection, Pattern.MULTILINE);
            if (!pattern.matcher(readmeContents).find()) {
                throw new RuntimeException("README should contain section: " + pattern.pattern());
            }
        }
    }
}
