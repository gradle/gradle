package org.gradle.sample;

import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

// tag::phased-sync[]
public class PhasedSync {
    public void sync(File projectDir) {
        AtomicReference<GradleBuild> buildStructure = new AtomicReference<>();
        AtomicReference<EclipseProjectResult> eclipseModel = new AtomicReference<>();

        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir)
                .connect()) {

            BuildActionExecuter<Void> executer = connection.action()
                .projectsLoaded(new FetchBuildStructureAction(), buildStructure::set)
                .buildFinished(new FetchEclipseModelAction(), eclipseModel::set)
                .build();

            try {
                executer
                    .forTasks("generateSources") // optional: tasks to run before the buildFinished action
                    .run();
            } catch (GradleConnectionException e) {
                // The operation failed, for example because a task failed or part of the
                // build could not be configured. The models that were already delivered
                // to the intermediate handlers above are still available.
            }
        }
    }
}
// end::phased-sync[]
