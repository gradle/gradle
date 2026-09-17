package org.gradle.sample;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;

// tag::projects-loaded-action[]
public class FetchBuildStructureAction implements BuildAction<GradleBuild> {
    @Override
    public GradleBuild execute(BuildController controller) {
        // The build structure is available as soon as the settings have been evaluated,
        // so this model can be queried from the projectsLoaded phase
        return controller.fetch(GradleBuild.class).getModel();
    }
}
// end::projects-loaded-action[]
