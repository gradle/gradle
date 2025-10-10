package org.example

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.problems.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Simple demo plugin for the Gradle Problems API.
 */
class HelloProblemsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.register("greet", GreetTask) { task ->
            task.group = "demo"
            task.description = "Greets a recipient. Demonstrates Problems API."
        }
    }
}

/**
 * Extra structured data to attach to problem reports.
 * Tools like Build Scans or IDEs could show this.
 */
interface GreetProblemData extends AdditionalData {
    String getConfiguredRecipient()
    void setConfiguredRecipient(String value)
}

/**
 * Custom task that uses the Gradle Problems API.
 */
abstract class GreetTask extends DefaultTask {

    @Input
    abstract Property<String> getRecipient()

// tag::problems-service[]
    @Inject
    abstract Problems getProblems()
// end::problems-service[]

// tag::problems-id[]
    private static final ProblemGroup GROUP =
        ProblemGroup.create("org.example.hello-problems", "Hello Problems")
    private static final ProblemId WARN_ID =
        ProblemId.create("missing-recipient", "Recipient not set", GROUP)
    private static final ProblemId FAIL_ID =
        ProblemId.create("forbidden-recipient", "Forbidden recipient 'fail'", GROUP)
// end::problems-id[]

    @TaskAction
    void run() {
// tag::problems-reporter[]
        def reporter = problems.reporter
// end::problems-reporter[]
        def name = recipient.orNull?.trim() ?: ""

        // Warning: missing recipient -> provide a helpful suggestion
        if (name.isEmpty()) {
// tag::problems-report[]
            reporter.report(WARN_ID) { spec ->
// tag::problems-spec[]
                spec.details("No recipient configured")
                    .severity(Severity.WARNING)
                    .solution('Set the recipient: tasks.greet { recipient = "World" }')
                    .documentedAt("https://gradle.org/hello-problems#recipient")
                    .additionalData(GreetProblemData) {
                        it.configuredRecipient = null
                    }
// end::problems-spec[]
            }
// end::problems-report[]
        }
        // Fatal: a specific value is disallowed to show throwing()
        else if (name.equalsIgnoreCase("fail")) {
// tag::problems-throw[]
            throw reporter.throwing(new GradleException("forbidden value"), FAIL_ID) { spec ->
                spec.details("Recipient 'fail' is not allowed")
                    .severity(Severity.ERROR)
                    .solution('Choose another value, e.g. recipient = "World".')
                    .documentedAt("https://gradle.org/hello-problems#forbidden")
                    .additionalData(GreetProblemData) {
                        it.configuredRecipient = name
                    }
            }
// end::problems-throw[]
        }

        logger.lifecycle("Hello, $name!")
    }
}
