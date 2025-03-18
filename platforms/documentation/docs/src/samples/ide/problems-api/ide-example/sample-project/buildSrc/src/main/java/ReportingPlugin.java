import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;

import javax.inject.Inject;

public class ReportingPlugin implements Plugin<Project> {

    private static final ProblemGroup REPORTING_PROBLEM_GROUP = ProblemGroup.create("reporting-plugin", "Display name, which should stay constant between reports");

    private final Problems problems;

    @Inject
    public ReportingPlugin(Problems problems) {
        this.problems = problems;
    }

    @Override
    public void apply(Project target) {
        // Emitting a problem when the plugin is applied
        problems.getReporter().report(
            ProblemId.create("initialization", "Initialization", REPORTING_PROBLEM_GROUP),
            spec -> spec
                .contextualLabel("A problem was detected when the plugin was loaded")
                .details("""
                Long format detail,
                possibly multiline,
                detailing what was happening.
                """)
        );

        // Emitting a problem when a task is configured
        target.getTasks().register(
            "problematicTask", task -> {
                throw problems.getReporter().throwing(
                    // The exception instance being thrown
                    // Behind the scenes, the exception will be bound to the problems emitted here
                    new GradleException("Configuration error detected"),
                    ProblemId.create("configuration", "Configuration", REPORTING_PROBLEM_GROUP),
                    spec -> spec
                        .contextualLabel("Fatal configuration error was detected")
                        .solution("Detailing how the situation could be fixed")
                        .documentedAt("a link to extra documentation about the problem")
                );
            }
        );
    }
}
