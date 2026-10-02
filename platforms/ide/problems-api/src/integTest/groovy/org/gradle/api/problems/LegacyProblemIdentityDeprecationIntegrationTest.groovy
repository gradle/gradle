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

import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import spock.lang.Issue

import static org.gradle.api.problems.fixtures.ReportingScript.getProblemReportingScript

/**
 * Problems whose identity was created through the legacy {@code ProblemGroup.create()} API are reported with a
 * deprecation warning, once per problem id, pointing at the predefined hierarchy.
 */
@Issue("https://github.com/gradle/gradle/issues/38670")
class LegacyProblemIdentityDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String DEPRECATION = "Reporting problem 'type (in generic)' with a group created through ProblemGroup.create(). " +
        "This behavior has been deprecated. This is scheduled to be removed in Gradle 10. " +
        "Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group(\"generic\"). " +
        "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#problems_api_legacy_identity"

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
        executer.expectDocumentedDeprecationWarning(DEPRECATION)
        executer.expectDocumentedDeprecationWarning(DEPRECATION.replace("'type (in generic)'", "'other (in generic)'"))
        run("reportProblem")

        then:
        receivedProblems.size() == 5
        2.times { findReceivedProblem { it.fqid == 'generic:type' } }
        findReceivedProblem { it.fqid == 'generic:other' }

        and: "each nag is also reported as a deprecation problem"
        def nag = findReceivedProblem { it.fqid == 'deprecation:legacy-problem-identity' && it.contextualLabel.contains("'type (in generic)'") }
        nag.contextualLabel == "Reporting problem 'type (in generic)' with a group created through ProblemGroup.create(). This behavior has been deprecated."
        nag.details == "This is scheduled to be removed in Gradle 10."
        nag.solutions == ['Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group("generic").']
        findReceivedProblem { it.fqid == 'deprecation:legacy-problem-identity' && it.contextualLabel.contains("'other (in generic)'") }
    }

    def "a legacy sub-group below a predefined group nags as well"() {
        given:
        withReportProblemTask """
            def group = ${ProblemGroup.name}.create("Lint", "Lint", problems.groups.compilation.java)
            problems.reporter.report(${ProblemId.name}.create("Unused import", "Unused import", group)) {}
        """

        when:
        executer.expectDocumentedDeprecationWarning(
            "Reporting problem 'Unused import (in Compilation > Java > Lint)' with a group created through ProblemGroup.create(). " +
                "This behavior has been deprecated. This is scheduled to be removed in Gradle 10. " +
                "Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group(\"Lint\"). " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#problems_api_legacy_identity")
        run("reportProblem")

        then:
        receivedProblems.size() == 2
        findReceivedProblem { it.fqid == 'Compilation:Java:Lint:Unused import' }
        findReceivedProblem { it.fqid == 'deprecation:legacy-problem-identity' }
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

    def "Gradle's own problems in legacy groups do not nag until they are migrated"() {
        given:
        withReportProblemTask """
            def id = ${ProblemId.name}.create("no-matches", "No matches", ${GradleCoreProblemGroup.name}.taskSelection())
            problems.reporter.report(id) {}
        """

        when:
        run("reportProblem")

        then:
        receivedProblems.size() == 1
        receivedProblem.fqid == 'task-selection:no-matches'
    }
}
