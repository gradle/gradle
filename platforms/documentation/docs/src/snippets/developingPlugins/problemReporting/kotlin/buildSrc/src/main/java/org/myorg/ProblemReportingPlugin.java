package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

// tag::snippet[]
public class ProblemReportingPlugin implements Plugin<Project> {

    public void apply(Project project) {
        // Register the problematic task
        project.getTasks().register("reportProblem", ProblemReporting.class);
    }
}
// end::snippet[]
