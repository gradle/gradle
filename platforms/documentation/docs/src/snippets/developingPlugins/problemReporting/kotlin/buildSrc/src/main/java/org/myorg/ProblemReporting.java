package org.myorg;

import java.io.File;
import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Severity;

/**
 * This is a class which pretends to be problematic.
 * <p>
 * It will, upon execution, report a problem if the configuration file does not exist.
 */
public class ProblemReporting extends DefaultTask {

    private final ProblemReporter problemReporter;

    @Inject
    public ProblemReporting(Problems problems) { // <1>
        this.problemReporter = problems.forNamespace("org.myorg"); // <2>
    }

    @TaskAction
    void reportProblem() {
        // Get the root directory of the project
        File rootDir = getProject().getRootDir();
        // Define the path to your config file
        File configFile = new File(rootDir, "config.yaml"); // <3>

        if (!configFile.exists()) {
            String detailsMessage = String.format("The configuration file '%s' does not exist.", configFile);
            // Report a problem if the config file does not exist
            this.problemReporter.throwing(builder -> builder // <4>
                .category("configuration", "missing-file")
                .label("Configuration file does not exist")
                .details(detailsMessage)
                .solution("Please create the configuration file.")
                .severity(Severity.ERROR)
                .withException(new IllegalStateException(detailsMessage))
            );
        }
    }
}
