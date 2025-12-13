package reporters;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.Severity;

import javax.inject.Inject;

/**
 * This is a simple, standard Gradle plugin that is applied to a project.
 */
public class StandardPlugin implements Plugin<Project> {

    public static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("sample-group", "Sample Group");

    private final Problems problems;

    @Inject
    public StandardPlugin(Problems problems) {
        this.problems = problems;
    }

    @Override
    public void apply(Project project) {
        project.getTasks().register("myFailingTask", FailingTask.class);
        // tag::problems-api-report[]
        ProblemId problemId = ProblemId.create("adhoc-plugin-deprecation", "Plugin is deprecated", PROBLEM_GROUP);
        problems.getReporter().report(problemId, problem -> problem
                .contextualLabel("The 'standard-plugin' is deprecated")
                .documentedAt("https://github.com/gradle/gradle/README.md")
                .severity(Severity.WARNING)
                .solution("Please use a more recent plugin version")
                .additionalData(SomeAdditionalData.class, additionalData -> {
                    additionalData.setName("Some name");
                    additionalData.setNames(java.util.Arrays.asList("name1", "name2"));
                })
        );
        // end::problems-api-report[]
    }
}
