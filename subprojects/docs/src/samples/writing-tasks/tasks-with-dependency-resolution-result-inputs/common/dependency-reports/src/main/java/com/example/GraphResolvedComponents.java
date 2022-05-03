package com.example;

import org.gradle.api.DefaultTask;
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
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public abstract class GraphResolvedComponents extends DefaultTask {

// tag::inputs[]
@Input
public abstract Property<ResolvedComponentResult> getRootComponent();
// end::inputs[]

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void action() throws IOException {
        File outputFile = getOutputFile().get().getAsFile();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            Set<ResolvedComponentResult> seen = new HashSet<>();
            reportComponent(getRootComponent().get(), writer, seen, "");
        }
        try (Stream<String> stream = Files.lines(outputFile.toPath())) {
            stream.forEach(System.out::println);
        }
    }

    private void reportComponent(
        ResolvedComponentResult component,
        PrintWriter writer,
        Set<ResolvedComponentResult> seen,
        String indent
    ) {
        writer.print(component.getId().getDisplayName());
        if (seen.add(component)) {
            writer.println();
            String newIndent = indent + "  ";
            for (DependencyResult dependency : component.getDependencies()) {
                writer.print(newIndent);
                writer.print(dependency.getRequested().getDisplayName());
                writer.print(" -> ");
                if (dependency instanceof ResolvedDependencyResult) {
                    ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
                    reportComponent(resolvedDependency.getSelected(), writer, seen, newIndent);
                } else {
                    writer.println(" -> not found");
                }
            }
        } else {
            writer.println(" (already seen)");
        }
    }
}
