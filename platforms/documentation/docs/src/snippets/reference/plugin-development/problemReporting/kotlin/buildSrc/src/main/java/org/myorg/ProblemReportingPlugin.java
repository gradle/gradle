package org.myorg;

import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.AdditionalData;

// tag::snippet[]
public class ProblemReportingPlugin implements Plugin<Project> {

    private final ProblemReporter reporter;
    private final ProblemId pluginDeprecated;

    interface SomeData extends AdditionalData {
        void setName(String name);
        String getName();
    }

    @Inject
    public ProblemReportingPlugin(Problems problems) { // <1>
        reporter = problems.getReporter();
        pluginDeprecated = problems.getGroups().getGradle().getBuildDefinition().problemId("Plugin 'x' is deprecated"); // <2>
    }

    public void apply(Project project) {
        reporter.report(pluginDeprecated, builder -> builder // <3>
            .details("The plugin 'x' is deprecated since version 2.5")
            .solution("Please use plugin 'y'")
            .additionalData(SomeData.class, additionalData -> {
                additionalData.setName("Some name"); // <4>
            })
        );
    }
}
// end::snippet[]
