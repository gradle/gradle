package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
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
        // tag::listResolvedArtifactIdentifiers[]
        ResolvableDependencies resolvableDependencies = project.getConfigurations().getByName("runtimeClasspath").getIncoming();
        Provider<Set<ResolvedArtifactResult>> resolvedArtifacts = resolvableDependencies.getArtifacts().getResolvedArtifacts();

        tasks.register("listResolvedArtifactIdentifiers", ListResolvedArtifactIdentifiers.class, task -> {

            task.getArtifactIdentifiers().set(resolvedArtifacts.map(result ->
                result.stream().map(ResolvedArtifactResult::getId).collect(toList())
            ));
        // end::listResolvedArtifactIdentifiers[]

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        // tag::listResolvedArtifactIdentifiers[]
        });
        // end::listResolvedArtifactIdentifiers[]

        tasks.register("listResolvedArtifactFiles", ListResolvedArtifactFiles.class, task -> {

            task.getArtifactFiles().from(resolvableDependencies.getArtifacts().getArtifactFiles());

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        });

        tasks.register("listResolvedArtifactFilesByIdentifiers", ListResolvedArtifactFilesByIdentifiers.class, task -> {

            task.getArtifactFiles().from(resolvableDependencies.getArtifacts().getArtifactFiles());
            task.getArtifactIdentifiers().set(resolvedArtifacts.map(result ->
                result.stream().map(ResolvedArtifactResult::getId).collect(toList())
            ));

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file(task.getName() + "/report.txt"));
        });

        tasks.register("graphResolvedComponentIdentifiers", GraphResolvedComponentIdentifiers.class, task -> {

            task.getRootComponent().set(resolvableDependencies.getResolutionResult().getRootComponent());

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
