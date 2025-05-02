package reporters;

import org.gradle.api.DefaultTask;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public abstract class FailingTask extends DefaultTask {

    @Inject
    public abstract Problems getProblems();


    @TaskAction
    public void run() {
        // tag::problems-api-fail-the-build[]
        ProblemId id = ProblemId.create("sample-error", "Sample Error", StandardPlugin.PROBLEM_GROUP);
        throw getProblems().getReporter().throwing((new RuntimeException("Message from runtime exception")), id, problemSpec -> {
            problemSpec.contextualLabel("This happened because ProblemReporter.throwing() was called")
                .details("This is a demonstration of how to add\ndetailed information to a build failure")
                .documentedAt("https://example.com/docs")
                .solution("Remove the Problems.throwing() method call from the task action");
        });
        // end::problems-api-fail-the-build[]
    }
}
