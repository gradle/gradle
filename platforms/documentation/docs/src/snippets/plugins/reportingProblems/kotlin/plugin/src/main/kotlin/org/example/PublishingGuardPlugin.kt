package org.example

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import javax.inject.Inject

/**
 * - version == "1" -> ERROR (throw; build fails)
 * - version == "2" -> WARNING (report; build succeeds)
 * - else           -> no problems
 */
abstract class PublishingGuardPlugin : Plugin<Project> {

    private val PLUGIN_GROUP: ProblemGroup =
        ProblemGroup.create("org.example.pubcheck", "Publishing Guard")

    // tag::problem_reporter[]
    @get:Inject
    abstract val problems: Problems

    override fun apply(project: Project) {
        val reporter = problems.reporter
    // end::problem_reporter[]

        project.tasks.register("pubcheck") { task ->
            task.group = "verification"
            task.description = "Demo Problems API: fails on version=1, warns on version=2."
            task.doLast {
                val versionStr = project.version?.toString()?.trim().orEmpty()

                if (versionStr == "1") {
                    // tag::problem_throw[]
                    // tag::problem_id[]
                    val id = ProblemId.create("pubcheck-version-one", "Version '1' not allowed", PLUGIN_GROUP)
                    // end::problem_id[]
                    // tag::problem_spec[]
                    reporter.throwing(id) { spec ->
                        spec.severity(Severity.ERROR)
                            .solution("Set project.version to '2' to see a warning, or any other value to pass.")
                            .documentedAt("https://gradle.org/pubcheck#version-1")
                    }
                    // end::problem_spec[]
                    // end::problem_throw[]
                }

                if (versionStr == "2") {
                    // tag::problem_report[]
                    val id = ProblemId.create("pubcheck-version-two", "Version '2' warning", PLUGIN_GROUP)
                    reporter.report(id) { spec ->
                        spec.severity(Severity.WARNING)
                            .solution("This is a demo warning. Use any other version to avoid it.")
                            .documentedAt("https://gradle.org/pubcheck#version-2")
                    }
                    // end::problem_report[]
                }
            }
        }
    // tag::problem_reporter[]
    }
    // end::problem_reporter[]
}
