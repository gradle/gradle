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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GraphResolvedArtifactFiles extends DefaultTask {

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
        outputFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            ResolvedComponentResult root = getRootComponent().get();
            writer.println("ROOT: " + root);
            List<ResolvedComponentResult> seen = new ArrayList<>();
            seen.add(root);
            printDependencies(writer, root, seen, filesByIdentifiers);
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

    private void printDependencies(PrintWriter writer, ResolvedComponentResult component, List<ResolvedComponentResult> seen, Map<ComponentIdentifier, File> filesByIdentifiers) {
        List<ResolvedDependencyResult> resolvedDependencies = new ArrayList<>();
        for (DependencyResult dep : component.getDependencies()) {
            if (dep instanceof ResolvedDependencyResult) {
                resolvedDependencies.add((ResolvedDependencyResult) dep);
            }
        }
        for (ResolvedDependencyResult dep : resolvedDependencies) {
            ComponentIdentifier from = component.getId();
            ComponentIdentifier to = dep.getSelected().getId();
            String filename = filesByIdentifiers.get(to).getName();
            writer.println("\t(" + from + ") => (" + to + ") => " + filename);
        }
        seen.add(component);
        for (ResolvedDependencyResult dep : resolvedDependencies) {
            if (!seen.contains(dep.getSelected())) {
                printDependencies(writer, dep.getSelected(), seen, filesByIdentifiers);
            }
        }
    }
}
