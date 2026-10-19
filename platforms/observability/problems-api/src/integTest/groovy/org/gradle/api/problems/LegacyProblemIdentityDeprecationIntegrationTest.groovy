/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.problems

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.fixtures.executer.ProblemsApiDeprecations
import spock.lang.Issue

import static org.gradle.api.problems.fixtures.ReportingScript.getProblemReportingScript

/**
 * Problems whose id was not created through the predefined groups, but through the legacy {@code ProblemId.create()}
 * and {@code ProblemGroup.create()} factories, are reported with a deprecation warning, once per problem id, pointing
 * at the predefined hierarchy.
 */
@Issue("https://github.com/gradle/gradle/issues/38670")
class LegacyProblemIdentityDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String PREDEFINED_GROUP_DEPRECATION = deprecation("Unused import (in Compilation > Java)",
        "Create the id with problemId() on the predefined group 'Compilation > Java' from problems.getGroups() instead.")

    private static String deprecation(String problem, String advice) {
        "Reporting problem '$problem' with an id not created through problems.getGroups(). " +
            "This behavior has been deprecated. This is scheduled to be removed in Gradle 10. " +
            "$advice Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/current/userguide/upgrading_version_9.html#problems_api_legacy_identity"
    }

    def setup() {
        enableProblemsApiCheck()
        expectLegacyProblemIdentityDeprecations()
    }

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportingScript(taskActionMethodBody)
    }

    def "reporting a problem with a legacy group nags once per problem id"() {
        given:
        withReportProblemTask """
            def group = ${ProblemGroup.name}.create("generic", "Generic")
            def id = ${ProblemId.name}.create("type", "label", group)
            problems.reporter.report(id) { it.contextualLabel("first") }
            problems.reporter.report(id) { it.contextualLabel("second") }
            problems.reporter.report(${ProblemId.name}.create("other", "Other", group)) {}
        """

        when:
        def advice = 'Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group("Generic").'
        executer.expectDocumentedDeprecationWarning(deprecation("type (in generic)", advice))
        executer.expectDocumentedDeprecationWarning(deprecation("other (in generic)", advice))
        run("reportProblem")

        then:
        receivedProblems.size() == 5
        2.times { findReceivedProblem { it.fqid == 'generic:type' } }
        findReceivedProblem { it.fqid == 'generic:other' }

        and: "each nag is also reported as a deprecation problem"
        def nag = findReceivedProblem { it.fqid == ProblemsApiDeprecations.LEGACY_IDENTITY_PROBLEM_FQID && it.contextualLabel.contains("'type (in generic)'") }
        nag.contextualLabel == "Reporting problem 'type (in generic)' with an id not created through problems.getGroups(). This behavior has been deprecated."
        nag.details == "This is scheduled to be removed in Gradle 10."
        nag.solutions == [advice]
        findReceivedProblem { it.fqid == ProblemsApiDeprecations.LEGACY_IDENTITY_PROBLEM_FQID && it.contextualLabel.contains("'other (in generic)'") }
    }

    def "a legacy sub-group below a predefined group nags as well"() {
        given:
        withReportProblemTask """
            def group = ${ProblemGroup.name}.create("Lint", "Lint", problems.groups.compilation.java)
            problems.reporter.report(${ProblemId.name}.create("Unused import", "Unused import", group)) {}
        """

        when:
        executer.expectDocumentedDeprecationWarning(deprecation("Unused import (in Compilation > Java > Lint)",
            "Create the group below the predefined group 'Compilation > Java' from problems.getGroups() instead, with group(\"Lint\")."))
        run("reportProblem")

        then:
        receivedProblems.size() == 2
        findReceivedProblem { it.fqid == 'Compilation:Java:Lint:Unused import' }
        findReceivedProblem { it.fqid == ProblemsApiDeprecations.LEGACY_IDENTITY_PROBLEM_FQID }
    }

    def "a legacy chain matching predefined groups nags, and the problem is reported in the predefined groups"() {
        given:
        withReportProblemTask """
            def group = ${ProblemGroup.name}.create("Java", "Java", ${ProblemGroup.name}.create("Compilation", "Compilation"))
            problems.reporter.report(${ProblemId.name}.create("Unused import", "Unused import", group)) {}
        """

        when:
        executer.expectDocumentedDeprecationWarning(PREDEFINED_GROUP_DEPRECATION)
        run("reportProblem")

        then:
        receivedProblems.size() == 2
        findReceivedProblem { it.fqid == 'Compilation:Java:Unused import' }
        findReceivedProblem { it.fqid == ProblemsApiDeprecations.LEGACY_IDENTITY_PROBLEM_FQID }
    }

    def "an id created through ProblemId.create() in a predefined group nags"() {
        given:
        withReportProblemTask """
            problems.reporter.report(${ProblemId.name}.create("Unused import", "Unused import", problems.groups.compilation.java)) {}
        """

        when:
        executer.expectDocumentedDeprecationWarning(PREDEFINED_GROUP_DEPRECATION)
        run("reportProblem")

        then:
        receivedProblems.size() == 2
        findReceivedProblem { it.fqid == 'Compilation:Java:Unused import' }
        findReceivedProblem { it.fqid == ProblemsApiDeprecations.LEGACY_IDENTITY_PROBLEM_FQID }
    }

    def "a legacy chain reported from a worker in another process nags"() {
        given:
        file("buildSrc/build.gradle") << "plugins { id 'java' }\ndependencies { implementation(gradleApi()) }"
        file("buildSrc/src/main/java/org/example/LegacyGroupWorkAction.java") << """
            package org.example;

            import javax.inject.Inject;
            import org.gradle.api.problems.ProblemGroup;
            import org.gradle.api.problems.ProblemId;
            import org.gradle.api.problems.Problems;
            import org.gradle.workers.WorkAction;
            import org.gradle.workers.WorkParameters;

            public abstract class LegacyGroupWorkAction implements WorkAction<WorkParameters.None> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void execute() {
                    ProblemGroup group = ProblemGroup.create("Java", "Java", ProblemGroup.create("Compilation", "Compilation"));
                    getProblems().getReporter().report(ProblemId.create("Unused import", "Unused import", group), problem -> {});
                }
            }
        """
        buildFile """
            abstract class ReportFromWorker extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void run() {
                    workerExecutor.processIsolation().submit(org.example.LegacyGroupWorkAction) {}
                }
            }

            tasks.register("reportFromWorker", ReportFromWorker)
        """

        when:
        executer.expectDocumentedDeprecationWarning(PREDEFINED_GROUP_DEPRECATION)
        run("reportFromWorker")

        then:
        receivedProblems.size() == 2
        findReceivedProblem { it.fqid == 'Compilation:Java:Unused import' }
        findReceivedProblem { it.fqid == ProblemsApiDeprecations.LEGACY_IDENTITY_PROBLEM_FQID }
    }

    def "problems from the predefined hierarchy do not nag"() {
        given:
        withReportProblemTask """
            problems.reporter.report(problems.groups.others.group("Generic").problemId("label")) {}
            problems.reporter.report(problems.groups.compilation.java.problemId("Unused import")) {}
        """

        when:
        run("reportProblem")

        then:
        receivedProblems.size() == 2
        receivedProblem(0).fqid == 'Compilation:Java:Unused import'
        receivedProblem(1).fqid == 'Others:Generic:label'
    }
}
