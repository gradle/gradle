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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.TaskProvider;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

public abstract class DependencyScannerPlugin implements Plugin<Project> {

    Attribute<Boolean> analyzed = Attribute.of("dependency-analyzed", Boolean.class);

    @Override
    public void apply(Project project) {

        // Configure artifact transform
        project.getDependencies().getAttributesSchema().attribute(analyzed);
        project.getDependencies().registerTransform(AnalyzingArtifactTransform.class, spec -> {
            spec.getFrom().attribute(analyzed, false);
            spec.getTo().attribute(analyzed, true);
        });

        // Configuration to declare analysis dependencies on.
        Configuration dependencyAnalysis = project.getConfigurations().create("dependencyAnalysis");
        dependencyAnalysis.setCanBeConsumed(false);
        dependencyAnalysis.setCanBeResolved(false);
        dependencyAnalysis.setVisible(true);

        // By default, we analyze the current project. Users can add additional dependencies themselves.
        dependencyAnalysis.getDependencies().add(project.getDependencies().create(project));

        // Per-project config:
        dependencyAnalysis.getDependencies().add(
            project.getDependencies().project(Collections.singletonMap("path", ":testing-jvm")));

        // For some reason the platform dependency isn't inherited from the project dependency.
        dependencyAnalysis.getDependencies().add(project.getDependencies().platform(
            project.getDependencies().project(Collections.singletonMap("path", ":distributions-dependencies"))
        ));

        // We will resolve this classpath to get the classes-to-analyze.
        Configuration dependencyAnalysisClasspath = project.getConfigurations().create("dependencyAnalysisClasspath");
        dependencyAnalysisClasspath.setCanBeResolved(true);
        dependencyAnalysisClasspath.setCanBeConsumed(false);
        dependencyAnalysisClasspath.setVisible(false);
        dependencyAnalysisClasspath.extendsFrom(dependencyAnalysis);

        // When we resolve this configuration, fetch the analysis for each dependency instead of the
        // dependency themselves.
        dependencyAnalysisClasspath.getAttributes().attribute(analyzed, true);

        // Resolve for runtime, since we don't have a compileElements to resolve for compile time instead.
        // Most importantly, we need the implementation/api deps. RuntimeOnly/compileOnly/compileOnlyApi are the least of our worries right now.
        ((ProjectInternal) project).getServices().get(JvmPluginServices.class).configureAsRuntimeClasspath(dependencyAnalysisClasspath);

        // TODO: Do we need to add this on classes too?
        project.getDependencies().getArtifactTypes().getByName(ArtifactTypeDefinition.JAR_TYPE).getAttributes()
            .attribute(analyzed, false);

//        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
//        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
//
        TaskProvider<DependencyScannerTask> scanOutputs = project.getTasks().register("scanOutputs", DependencyScannerTask.class, task -> {
            task.getAnalyzedClasspath().set(dependencyAnalysisClasspath);
        });

        TaskProvider<Exec> viz = project.getTasks().register("graphviz", Exec.class, task -> {
            task.args("-Tsvg", "-o",
                project.getLayout().getBuildDirectory().file("graph.svg").get().getAsFile().getAbsolutePath(),
                scanOutputs.get().getOutputFile().get().getAsFile().getAbsolutePath());
            task.executable("/opt/homebrew/bin/dot");
        });
        viz.get().dependsOn(scanOutputs);

//        scanOutputs.get().dependsOn(main.getCompileTaskName("java"));
    }
}
