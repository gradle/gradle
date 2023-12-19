package org.myorg;

import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Severity;

// tag::snippet[]
public class ProblemReportingPlugin implements Plugin<Project> {

    private final ProblemReporter problemReporter;

    @Inject
    public ProblemReportingPlugin(Problems problems) { // <1>
        this.problemReporter = problems.forNamespace("org.myorg"); // <2>
    }

    public void apply(Project project) {
        this.problemReporter.reporting(builder -> builder // <3>
            .label("Plugin 'x' is deprecated")
            .details("The plugin 'x' is deprecated since version 2.5")
            .solution("Please use plugin 'y'")
            .severity(Severity.WARNING)
        );
    }
}
// end::snippet[]
