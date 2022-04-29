package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class GraphResolvedComponentIdentifiers extends DefaultTask {

// tag::inputs[]
@Input
public abstract Property<ResolvedComponentResult> getRootComponent();
// end::inputs[]

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void action() throws IOException {
        File outputFile = getOutputFile().get().getAsFile();
        outputFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            ResolvedComponentResult root = getRootComponent().get();
            writer.println("ROOT: " + root);
            List<ResolvedComponentResult> seen = new ArrayList<>();
            seen.add(root);
            printDependencies(writer, root, seen);
        }
    }

    private void printDependencies(
            PrintWriter writer,
            ResolvedComponentResult component,
            List<ResolvedComponentResult> seen
    ) {
        List<ResolvedDependencyResult> resolvedDependencies = new ArrayList<>();
        for (DependencyResult dep : component.getDependencies()) {
            if (dep instanceof ResolvedDependencyResult) {
                resolvedDependencies.add((ResolvedDependencyResult) dep);
            }
        }
        for (ResolvedDependencyResult dep : resolvedDependencies) {
            ComponentIdentifier from = component.getId();
            ComponentIdentifier to = dep.getSelected().getId();
            writer.println("\t" + from + " => " + to);

        }
        seen.add(component);
        for (ResolvedDependencyResult dep : resolvedDependencies) {
            if (!seen.contains(dep.getSelected())) {
                printDependencies(writer, dep.getSelected(), seen);
            }
        }
    }
}
