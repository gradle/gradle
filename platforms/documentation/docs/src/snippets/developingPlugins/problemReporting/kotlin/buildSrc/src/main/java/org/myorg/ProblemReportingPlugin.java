package org.myorg;

import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.AdditionalData;

// tag::snippet[]
public class ProblemReportingPlugin implements Plugin<Project> {

    public static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("sample-group", "Sample Group");

    private final ProblemReporter problemReporter;

    interface SomeData extends AdditionalData {
        void setName(String name);
        String getName();
    }

    @Inject
    public ProblemReportingPlugin(Problems problems) { // <1>
        this.problemReporter = problems.getReporter(); // <2>
    }

    public void apply(Project project) {
        ProblemId problemId = ProblemId.create("adhoc-deprecation", "Plugin 'x' is deprecated", PROBLEM_GROUP);
        this.problemReporter.report(problemId, builder -> builder // <3>
            .details("The plugin 'x' is deprecated since version 2.5")
            .solution("Please use plugin 'y'")
            .severity(Severity.WARNING)
            .additionalData(SomeData.class, additionalData -> {
                additionalData.setName("Some name");
            })
        );
    }
}
// end::snippet[]
