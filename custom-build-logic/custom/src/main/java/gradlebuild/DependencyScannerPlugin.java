/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JvmEcosystemPlugin;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;

public abstract class DependencyScannerPlugin implements Plugin<Project> {

    Attribute<Boolean> analyzed = Attribute.of("dependency-analyzed", Boolean.class);

    @Override
    public void apply(Project project) {

        project.getPlugins().apply(JvmEcosystemPlugin.class);

        // Configure artifact transform
        project.getDependencies().getAttributesSchema().attribute(analyzed);
        project.getDependencies().registerTransform(AnalyzingArtifactTransform.class, spec -> {
            spec.getFrom().attribute(analyzed, false);
            spec.getTo().attribute(analyzed, true);
        });

        // Configuration to declare analysis dependencies on.
        Configuration dependencyScanner = project.getConfigurations().create("dependencyScanner");
        dependencyScanner.setCanBeConsumed(false);
        dependencyScanner.setCanBeResolved(false);
        dependencyScanner.setVisible(true);

//        // By default, we analyze the current project. Users can add additional dependencies themselves.
//        dependencyScanner.getDependencies().add(project.getDependencies().create(project));

        // Per-project config:
//        dependencyScanner.getDependencies().add(
//            project.getDependencies().project(Collections.singletonMap("path", ":testing-jvm-infrastructure")));

        // For some reason the platform dependency isn't inherited from the project dependency.
        dependencyScanner.getDependencies().add(project.getDependencies().platform(
            project.getDependencies().project(Collections.singletonMap("path", ":distributions-dependencies"))
        ));

        // We will resolve this classpath to get the classes-to-analyze.
        Configuration dependencyAnalysisClasspath = project.getConfigurations().create("dependencyAnalysisClasspath");
        dependencyAnalysisClasspath.setCanBeResolved(true);
        dependencyAnalysisClasspath.setCanBeConsumed(false);
        dependencyAnalysisClasspath.setVisible(false);
        dependencyAnalysisClasspath.extendsFrom(dependencyScanner);

        // When we resolve this configuration, fetch the analysis for each dependency instead of the
        // dependency themselves.
        dependencyAnalysisClasspath.getAttributes().attribute(analyzed, true);

        // Resolve for runtime, since we don't have a compileElements to resolve for compile time instead.
        // Most importantly, we need the implementation/api deps. RuntimeOnly/compileOnly/compileOnlyApi are the least of our worries right now.
        ((ProjectInternal) project).getServices().get(JvmPluginServices.class).configureAsRuntimeClasspath(dependencyAnalysisClasspath);

        // TODO: Do we need to add this on classes too?
        project.getDependencies().getArtifactTypes().getByName(ArtifactTypeDefinition.JAR_TYPE).getAttributes()
            .attribute(analyzed, false);




        TaskProvider<D3GraphWriterTask> d3 = project.getTasks().register("renderD3Json", D3GraphWriterTask.class, task -> {
            task.getAnalyzedClasspath().set(dependencyAnalysisClasspath);
        });

        TaskProvider<Task> html = project.getTasks().register("generateD3Html", task -> {

            Provider<RegularFile> outputFile = project.getLayout().getBuildDirectory().file("force-graph.html");
            task.getOutputs().file(outputFile);

            task.dependsOn(d3.get());

            task.doLast(t -> {
                try(InputStream input = DependencyScannerPlugin.class.getResource("force-graph.html").openStream()) {
                    try (OutputStream output = Files.newOutputStream(outputFile.get().getAsFile().toPath())) {
                        input.transferTo(output);
                    }
                } catch (IOException e) {
                    throw new GradleException("Failed to write HTML", e);
                }
            });
        });






//        setupDygraph(project, dependencyAnalysisClasspath);
    }

    private void setupDygraph(Project project, Configuration dependencyAnalysisClasspath) {
        TaskProvider<DependencyScannerTask> scanOutputs = project.getTasks().register("scanOutputs", DependencyScannerTask.class, task -> {
            task.getAnalyzedClasspath().set(dependencyAnalysisClasspath);
//            task.getAnalyzedClasspath().set(dependencyAnalysisClasspath.getIncoming().getArtifacts().getResolvedArtifacts());
        });

        TaskProvider<Exec> viz = project.getTasks().register("graphviz", Exec.class, task -> {
            task.args("-Tsvg", "-o",
                project.getLayout().getBuildDirectory().file("graph.svg").get().getAsFile().getAbsolutePath(),
                scanOutputs.get().getOutputFile().get().getAsFile().getAbsolutePath());
            task.executable("/opt/homebrew/bin/dot");
        });
        viz.get().dependsOn(scanOutputs);
    }
}
