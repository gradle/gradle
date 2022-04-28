package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class ListResolvedArtifactFilesByIdentifiers extends DefaultTask {

    @Input
    public abstract ListProperty<ComponentArtifactIdentifier> getArtifactIdentifiers();

    @InputFiles
    public abstract ConfigurableFileCollection getArtifactFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void action() throws IOException {
        File outputFile = getOutputFile().get().getAsFile();
        outputFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            List<File> files = new ArrayList<>(getArtifactFiles().getFiles());
            List<ComponentArtifactIdentifier> ids = getArtifactIdentifiers().get();
            for (int index = 0; index < ids.size(); index++) {
                writer.println(ids.get(index).getComponentIdentifier() + " => " + files.get(index).getName());
            }
        }
    }
}
