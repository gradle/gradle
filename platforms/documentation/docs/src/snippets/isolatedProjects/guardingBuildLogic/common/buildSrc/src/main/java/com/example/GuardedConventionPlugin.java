package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;

import javax.inject.Inject;

// tag::guarded-plugin[]
public abstract class GuardedConventionPlugin implements Plugin<Project> {

    @Inject
    protected abstract BuildFeatures getBuildFeatures(); // <1>

    @Override
    public void apply(Project project) {
        if (getBuildFeatures().getIsolatedProjects().getActive().get()) { // <2>
            project.getLogger().warn(
                "Skipping " + getClass().getSimpleName() + " on " + project.getPath()
                    + ", because Isolated Projects is enabled");
            return; // <3>
        }
        configureIncompatibleBuildLogic(project);
    }

    private void configureIncompatibleBuildLogic(Project project) {
        // Build logic that is not (yet) compatible with Isolated Projects.
    }
}
// end::guarded-plugin[]
