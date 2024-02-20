package reporters;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;

import javax.inject.Inject;

/**
 * This is a simple, standard Gradle plugin that is applied to a project.
 */
public class StandardPlugin implements Plugin<Project> {

    private final Problems problems;

    @Inject
    public StandardPlugin(Problems problems) {
        this.problems = problems;
    }

    @Override
    public void apply(Project target) {
        // TODO check rendered sample content
        // tag::problems-api-report[]
        problems.forNamespace("reporters.standard.plugin").reporting(problem -> problem
                .id("Plugin is deprecated")
                .contextualMessage("The 'standard-plugin' is deprecated")
                .documentedAt("https://github.com/gradle/gradle/README.md")
                .severity(Severity.WARNING)
                .solution("Please use a more recent plugin version")
        );
        // end::problems-api-report[]
    }
}
