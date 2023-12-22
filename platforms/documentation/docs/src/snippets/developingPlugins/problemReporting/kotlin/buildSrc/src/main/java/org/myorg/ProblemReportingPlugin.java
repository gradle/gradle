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
        // Get the root directory of the project
        File rootDir = project.getRootDir();
        // Define the path to your config file
        File configFile = new File(rootDir, "config.yaml"); // <3>

        if (!configFile.exists()) {
            // Report a problem if the config file does not exist
            this.problemReporter.throwing(builder -> builder // <4>
                .category("configuration", "missing-file")
                .label("Configuration file does not exist")
                .details(String.format("The configuration file '%s' does not exist.", configFile))
                .solution("Please create the configuration file.")
                .severity(Severity.ERROR)
            );
        }
    }
}
// end::snippet[]
