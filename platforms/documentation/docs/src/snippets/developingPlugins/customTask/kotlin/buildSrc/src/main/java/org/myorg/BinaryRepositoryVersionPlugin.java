package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

// tag::snippet[]
public class BinaryRepositoryVersionPlugin implements Plugin<Project> {
    public void apply(Project project) {
        BinaryRepositoryExtension extension =
            project.getExtensions().create("binaryRepo", BinaryRepositoryExtension.class);

        project.getTasks().register("latestArtifactVersion", LatestArtifactVersion.class, task -> {
            task.getCoordinates().set(extension.getCoordinates());
            task.getServerUrl().set(extension.getServerUrl());
        });
    }
}
// end::snippet[]
