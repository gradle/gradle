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
    private final ProblemId pluginMisconfigured;

    interface SomeData extends AdditionalData {
        void setName(String name);
        String getName();
    }

    @Inject
    public ProblemReportingPlugin(Problems problems) { // <1>
        reporter = problems.getReporter();
        pluginMisconfigured = problems.getGroups().getGradle().getBuildDefinition().problemId("Plugin 'x' is misconfigured"); // <2>
    }

    public void apply(Project project) {
        reporter.report(pluginMisconfigured, builder -> builder // <3>
            .details("The property 'outputDirectory' of plugin 'x' is not set")
            .solution("Set 'outputDirectory' in the 'x' extension")
            .additionalData(SomeData.class, additionalData -> {
                additionalData.setName("Some name"); // <4>
            })
        );
    }
}
// end::snippet[]
