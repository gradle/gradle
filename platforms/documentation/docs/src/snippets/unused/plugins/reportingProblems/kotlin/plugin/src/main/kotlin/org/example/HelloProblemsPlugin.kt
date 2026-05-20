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
abstract class HelloProblemsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("greet", GreetTask::class.java) {
            group = "demo"
            description = "Greets a recipient. Demonstrates Problems API."
        }
    }
}

/**
 * Extra structured data to attach to problem reports.
 * Tools like Build Scans or IDEs could show this.
 */
interface GreetProblemData : AdditionalData {
    var configuredRecipient: String?
}

/**
 * Custom task that uses the Gradle Problems API.
 */
abstract class GreetTask : DefaultTask() {

    @get:Input
    abstract val recipient: Property<String>

// tag::problems-service[]
    @get:Inject
    abstract val problems: Problems
// end::problems-service[]

// tag::problems-id[]
    private val GROUP: ProblemGroup =
        ProblemGroup.create("org.example.hello-problems", "Hello Problems")
    private val WARN_ID: ProblemId =
        ProblemId.create("missing-recipient", "Recipient not set", GROUP)
    private val FAIL_ID: ProblemId =
        ProblemId.create("forbidden-recipient", "Forbidden recipient 'fail'", GROUP)
// end::problems-id[]

    @TaskAction
    fun run() {
// tag::problems-reporter[]
        val reporter = problems.reporter
// end::problems-reporter[]
        val name = recipient.orNull?.trim().orEmpty()

        // Warning: missing recipient -> provide a helpful suggestion
        if (name.isEmpty()) {
// tag::problems-report[]
            reporter.report(WARN_ID) {
// tag::problems-spec[]
                details("No recipient configured")
                severity(Severity.WARNING)
                solution("""Set the recipient: tasks.greet { recipient = "World" }""")
                documentedAt("https://gradle.org/hello-problems#recipient")
                additionalData(GreetProblemData::class.java) {
                    configuredRecipient = null
                }
// end::problems-spec[]
            }
// end::problems-report[]
        }

        // Fatal: a specific value is disallowed to show throwing()
        else if (name.equals("fail", ignoreCase = true)) {
// tag::problems-throw[]
            throw reporter.throwing(GradleException("forbidden value"), FAIL_ID) {
                details("Recipient 'fail' is not allowed")
                severity(Severity.ERROR)
                solution("""Choose another value, e.g. recipient = "World".""")
                documentedAt("https://gradle.org/hello-problems#forbidden")
                additionalData(GreetProblemData::class.java) {
                    configuredRecipient = name
                }
            }
// end::problems-throw[]
        }

        logger.lifecycle("Hello, $name!")
    }
}
