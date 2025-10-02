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
abstract class PublishingGuardPlugin implements Plugin<Project> {

    private static final ProblemGroup PLUGIN_GROUP =
            ProblemGroup.create('org.example.pubcheck', 'Publishing Guard')

    // tag::problem_reporter[]
    @Inject
    abstract Problems getProblems()

    @Override
    void apply(Project project) {
        def reporter = problems.reporter
    // end::problem_reporter[]

        project.tasks.register('pubcheck') { t ->
            t.group = 'verification'
            t.description = "Demo Problems API: fails on version=1, warns on version=2."

            t.doLast {
                String versionStr = String.valueOf(project.version ?: '').trim()

                if (versionStr == '1') {
                    // tag::problem_throw[]
                    // tag::problem_id[]
                    ProblemId id = ProblemId.create('pubcheck-version-one', "Version '1' not allowed", PLUGIN_GROUP)
                    // end::problem_id[]
                    // tag::problem_spec[]
                    reporter.throwing(id) { spec ->
                        spec.severity(Severity.ERROR)
                                .solution("Set project.version to '2' to see a warning, or any other value to pass.")
                                .documentedAt('https://example.org/pubcheck#version-1')
                    }
                    // end::problem_spec[]
                    // end::problem_throw[]
                }

                if (versionStr == '2') {
                    // tag::problem_report[]
                    ProblemId id = ProblemId.create('pubcheck-version-two', "Version '2' warning", PLUGIN_GROUP)
                    reporter.report(id) { spec ->
                        spec.severity(Severity.WARNING)
                                .solution('This is a demo warning. Use any other version to avoid it.')
                                .documentedAt('https://example.org/pubcheck#version-2')
                    }
                    // end::problem_report[]
                }
            }
        }
    // tag::problem_reporter[]
    }
    // end::problem_reporter[]
}
