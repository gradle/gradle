package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

public abstract class GraphResolvedComponentsAndFiles extends DefaultTask {

    @Input
    public abstract ListProperty<ComponentArtifactIdentifier> getArtifactIdentifiers();

    @InputFiles
    public abstract ConfigurableFileCollection getArtifactFiles();

    @Input
    public abstract Property<ResolvedComponentResult> getRootComponent();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void action() throws IOException {
        Map<ComponentIdentifier, File> filesByIdentifiers = filesByIdentifiers();
        File outputFile = getOutputFile().get().getAsFile();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            Set<ResolvedComponentResult> seen = new HashSet<>();
            reportComponent(getRootComponent().get(), writer, seen, filesByIdentifiers, "");
        }
        try (Stream<String> stream = Files.lines(outputFile.toPath())) {
            stream.forEach(System.out::println);
        }
    }

    private void reportComponent(
        ResolvedComponentResult component,
        PrintWriter writer,
        Set<ResolvedComponentResult> seen,
        Map<ComponentIdentifier, File> filesByIdentifiers,
        String indent
    ) {
        writer.print(component.getId().getDisplayName());
        File file = filesByIdentifiers.get(component.getId());
        if (file != null) {
            writer.print(" => ");
            writer.print(file.getName());
        }
        if (seen.add(component)) {
            writer.println();
            String newIndent = indent + "  ";
            for (DependencyResult dependency : component.getDependencies()) {
                writer.print(newIndent);
                writer.print(dependency.getRequested().getDisplayName());
                writer.print(" -> ");
                if (dependency instanceof ResolvedDependencyResult) {
                    ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
                    reportComponent(resolvedDependency.getSelected(), writer, seen, filesByIdentifiers, newIndent);
                } else {
                    writer.println(" -> not found");
                }
            }
        } else {
            writer.println(" (already seen)");
        }
    }

    private Map<ComponentIdentifier, File> filesByIdentifiers() {
        Map<ComponentIdentifier, File> map = new HashMap<>();
        List<ComponentArtifactIdentifier> identifiers = getArtifactIdentifiers().get();
        List<File> files = new ArrayList<>(getArtifactFiles().getFiles());
        for (int index = 0; index < identifiers.size(); index++) {
            map.put(identifiers.get(index).getComponentIdentifier(), files.get(index));
        }
        return map;
    }
}
