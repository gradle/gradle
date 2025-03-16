package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Adds an "example" extension to project and wires dependencies from the extension to a Configuration.
 */
public class ExamplePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ExampleExtension example = project.getExtensions().create("example", ExampleExtension.class);
// tag::wire-dependencies[]
        project.getConfigurations().dependencyScope("exampleImplementation", conf -> {
            conf.fromDependencyCollector(example.getDependencies().getImplementation());
        });
// end::wire-dependencies[]
    }
}
