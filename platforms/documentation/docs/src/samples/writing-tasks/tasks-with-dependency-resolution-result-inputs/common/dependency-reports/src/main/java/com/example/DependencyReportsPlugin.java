package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public abstract class DependencyReportsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        project.getPluginManager().withPlugin("java-base", plugin -> {

            ProjectLayout layout = project.getLayout();
            ConfigurationContainer configurations = project.getConfigurations();
            TaskContainer tasks = project.getTasks();

            tasks.register("listResolvedArtifacts", ListResolvedArtifacts.class, task -> {

// tag::listResolvedArtifacts[]
Configuration runtimeClasspath = configurations.getByName("runtimeClasspath");
Provider<Set<ResolvedArtifactResult>> artifacts = runtimeClasspath.getIncoming().getArtifacts().getResolvedArtifacts();

task.getArtifactIds().set(artifacts.map(new IdExtractor()));

// end::listResolvedArtifacts[]

                task.getArtifactVariants().set(artifacts.map(new VariantExtractor()));
                task.getArtifactFiles().set(artifacts.map(new FileExtractor(layout)));

                task.getOutputFile().set(layout.getBuildDirectory().file(task.getName() + "/report.txt"));
            });

            tasks.register("graphResolvedComponents", GraphResolvedComponents.class, task -> {

// tag::graphResolvedComponents[]
Configuration runtimeClasspath = configurations.getByName("runtimeClasspath");

task.getRootComponent().set(
    runtimeClasspath.getIncoming().getResolutionResult().getRootComponent()
);
// end::graphResolvedComponents[]

                task.getOutputFile().set(layout.getBuildDirectory().file(task.getName() + "/report.txt"));
            });

            tasks.register("graphResolvedComponentsAndFiles", GraphResolvedComponentsAndFiles.class, task -> {

                ResolvableDependencies resolvableDependencies = configurations.getByName("runtimeClasspath").getIncoming();
                Provider<Set<ResolvedArtifactResult>> resolvedArtifacts = resolvableDependencies.getArtifacts().getResolvedArtifacts();

                task.getArtifactFiles().from(resolvableDependencies.getArtifacts().getArtifactFiles());
                task.getArtifactIdentifiers().set(resolvedArtifacts.map(result -> result.stream().map(ResolvedArtifactResult::getId).collect(toList())));
                task.getRootComponent().set(resolvableDependencies.getResolutionResult().getRootComponent());

                task.getOutputFile().set(layout.getBuildDirectory().file(task.getName() + "/report.txt"));
            });
        });
    }

// tag::listResolvedArtifacts[]
static class IdExtractor
    implements Transformer<List<ComponentArtifactIdentifier>, Collection<ResolvedArtifactResult>> {
    @Override
    public List<ComponentArtifactIdentifier> transform(Collection<ResolvedArtifactResult> artifacts) {
        return artifacts.stream().map(ResolvedArtifactResult::getId).collect(Collectors.toList());
    }
}
// end::listResolvedArtifacts[]

    static class VariantExtractor implements Transformer<List<ResolvedVariantResult>, Collection<ResolvedArtifactResult>> {
        @Override
        public List<ResolvedVariantResult> transform(Collection<ResolvedArtifactResult> artifacts) {
            return artifacts.stream().map(ResolvedArtifactResult::getVariant).collect(Collectors.toList());
        }
    }

    static class FileExtractor implements Transformer<List<RegularFile>, Collection<ResolvedArtifactResult>> {
        private final ProjectLayout projectLayout;

        public FileExtractor(ProjectLayout projectLayout) {
            this.projectLayout = projectLayout;
        }

        @Override
        public List<RegularFile> transform(Collection<ResolvedArtifactResult> artifacts) {
            Directory projectDirectory = projectLayout.getProjectDirectory();
            return artifacts.stream().map(a -> projectDirectory.file(a.getFile().getAbsolutePath())).collect(Collectors.toList());
        }
    }
}
