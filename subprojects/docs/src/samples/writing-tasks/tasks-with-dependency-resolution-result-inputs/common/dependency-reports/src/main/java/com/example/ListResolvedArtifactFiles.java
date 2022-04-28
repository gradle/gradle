package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public abstract class ListResolvedArtifactFiles extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getArtifactFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void action() throws IOException {
        File outputFile = getOutputFile().get().getAsFile();
        outputFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            for (File file : getArtifactFiles().getFiles()) {
                writer.println(file.getName());
            }
        }
    }
}
