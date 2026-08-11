package org.gradle.sample;

// tag::phased-action[]
import org.gradle.tooling.BuildException;
import org.gradle.tooling.ProjectConnection;

import java.util.ArrayList;
import java.util.List;

public class ResilientSyncClient {
    public List<String> fetchProjects(ProjectConnection connection) {
        List<String> projects = new ArrayList<>();

        try {
            connection.action()
                .buildFinished(new FetchProjectsAction(), projects::addAll)
                .build()
                .run();
        } catch (BuildException e) {
            // The sync failed, but the intermediate handler above has
            // already received the models Gradle was able to build.
        }

        // 'projects' holds the partial result, even though the sync failed.
        return projects;
    }
}
// end::phased-action[]
