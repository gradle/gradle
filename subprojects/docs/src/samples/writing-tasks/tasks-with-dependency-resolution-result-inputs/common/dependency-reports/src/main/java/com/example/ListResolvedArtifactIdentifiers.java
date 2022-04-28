package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

// tag::inputs[]
public abstract class ListResolvedArtifactIdentifiers extends DefaultTask {

    @Input
    public abstract ListProperty<ComponentArtifactIdentifier> getArtifactIdentifiers();
// end::inputs[]

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void action() throws IOException {
        File outputFile = getOutputFile().get().getAsFile();
        outputFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            for (ComponentArtifactIdentifier id : getArtifactIdentifiers().get()) {
                writer.println(id.getComponentIdentifier());
            }
        }
    }
// tag::inputs[]
}
// end::inputs[]
