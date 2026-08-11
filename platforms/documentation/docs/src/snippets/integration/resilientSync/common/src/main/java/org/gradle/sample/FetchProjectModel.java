package org.gradle.sample;

// tag::fetch-model[]
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

import java.util.ArrayList;
import java.util.List;

public class FetchProjectModel implements BuildAction<List<String>> {
    @Override
    public List<String> execute(BuildController controller) {
        FetchModelResult<GradleProject> result = controller.fetch(GradleProject.class);

        for (Failure failure : result.getFailures()) {
            System.out.println("Model could not be built: " + failure.getMessage());
        }

        GradleProject model = result.getModel();
        if (model == null) {
            // The model could not be built; result.getFailures() explains why.
            return new ArrayList<>();
        }

        List<String> taskNames = new ArrayList<>();
        for (GradleTask task : model.getTasks()) {
            taskNames.add(task.getName());
        }
        return taskNames;
    }
}
// end::fetch-model[]
