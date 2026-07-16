package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;

import javax.inject.Inject;

// tag::applier-plugin[]
public abstract class ApplierConventionPlugin implements Plugin<Project> {

    @Inject
    protected abstract BuildFeatures getBuildFeatures();

    @Override
    public void apply(Project project) {
        if (getBuildFeatures().getIsolatedProjects().getActive().get()) { // <1>
            project.getLogger().warn(
                "Not applying com.example.incompatible on " + project.getPath()
                    + " because it is incompatible with Isolated Projects");
            return;
        }
        project.getPluginManager().apply("com.example.incompatible");
    }
}
// end::applier-plugin[]
