package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;

import java.util.Set;

import static java.util.stream.Collectors.toList;

public abstract class DependencyReportsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        project.getPluginManager().apply("java-library");

        TaskContainer tasks = project.getTasks();
        ConfigurationContainer configurations = project.getConfigurations();
        ResolvableDependencies resolvableDependencies = configurations.getByName("runtimeClasspath").getIncoming();

        tasks.register("listResolvedArtifactIdentifiers", ListResolvedArtifactIdentifiers.class, task -> {

// tag::listResolvedArtifactIdentifiers[]
Configuration runtimeClasspath = configurations.getByName("runtimeClasspath");
Provider<Set<ResolvedArtifactResult>> resolvedArtifacts = runtimeClasspath.getIncoming().getArtifacts().getResolvedArtifacts();

task.getArtifactIdentifiers().set(
    resolvedArtifacts.map(result ->
        result.stream().map(ResolvedArtifactResult::getId).collect(toList())
    )
);
// end::listResolvedArtifactIdentifiers[]

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        });

        tasks.register("listResolvedArtifactFiles", ListResolvedArtifactFiles.class, task -> {

            task.getArtifactFiles().from(resolvableDependencies.getArtifacts().getArtifactFiles());

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        });

        tasks.register("listResolvedArtifactFilesByIdentifiers", ListResolvedArtifactFilesByIdentifiers.class, task -> {

            Provider<Set<ResolvedArtifactResult>> resolvedArtifacts = resolvableDependencies.getArtifacts().getResolvedArtifacts();

            task.getArtifactFiles().from(resolvableDependencies.getArtifacts().getArtifactFiles());
            task.getArtifactIdentifiers().set(resolvedArtifacts.map(result ->
                result.stream().map(ResolvedArtifactResult::getId).collect(toList())
            ));

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        });

        tasks.register("graphResolvedComponentIdentifiers", GraphResolvedComponentIdentifiers.class, task -> {

// tag::graphResolvedComponentIdentifiers[]
Configuration runtimeClasspath = configurations.getByName("runtimeClasspath");

task.getRootComponent().set(
    runtimeClasspath.getIncoming().getResolutionResult().getRootComponent()
);
// end::graphResolvedComponentIdentifiers[]

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        });

        tasks.register("graphResolvedArtifactFiles", GraphResolvedArtifactFiles.class, task -> {

            task.getArtifactFiles().from(resolvableDependencies.getArtifacts().getArtifactFiles());
            task.getArtifactIdentifiers().set(resolvedArtifacts.map(result ->
                result.stream().map(ResolvedArtifactResult::getId).collect(toList())
            ));
            task.getRootComponent().set(resolvableDependencies.getResolutionResult().getRootComponent());

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        });
    }
}
