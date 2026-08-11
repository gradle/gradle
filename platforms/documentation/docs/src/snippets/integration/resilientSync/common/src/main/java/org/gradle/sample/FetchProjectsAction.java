package org.gradle.sample;

// tag::fetch-projects-action[]
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.List;

public class FetchProjectsAction implements BuildAction<List<String>> {
    @Override
    public List<String> execute(BuildController controller) {
        FetchModelResult<GradleBuild> result = controller.fetch(GradleBuild.class);

        List<String> projectNames = new ArrayList<>();
        GradleBuild model = result.getModel();
        if (model != null) {
            for (BasicGradleProject project : model.getProjects()) {
                projectNames.add(project.getName());
            }
        }
        return projectNames;
    }
}
// end::fetch-projects-action[]
