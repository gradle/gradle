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

import static org.gradle.api.problems.fixtures.ReportingScript.getProblemReportingScript

class LegacyProblemIdentityDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String UPGRADE_GUIDE = "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#problems_api_legacy_identity"

    def setup() {
        enableProblemsApiCheck()
    }

    def "calling #method nags"() {
        given:
        buildFile getProblemReportingScript("""
            def group = problems.groups.others.group("Sample Group")
            def id = group.problemId("Sample problem")
            println("value: " + $call)
        """)

        when:
        executer.expectDocumentedDeprecationWarning("The $method method has been deprecated. This is scheduled to be removed in Gradle 10. $advice $UPGRADE_GUIDE")
        run("reportProblem")

        then:
        outputContains("value: $expected")
        receivedProblem.definition.id.fqid == "Gradle:Deprecation:Method usage"

        where:
        method                          | call                          | expected         | advice
        "ProblemGroup.getDisplayName()" | "group.getDisplayName()"      | "Sample Group"   | "Please use the getName() method instead."
        "ProblemId.getDisplayName()"    | "id.getDisplayName()"         | "Sample problem" | "Please use the getName() method instead."
        "ProblemGroup.getParent()"      | "group.getParent().getName()" | "Others"         | "The predefined groups from Problems.getGroups() have a fixed place in the hierarchy."
    }

    def "subclassing ProblemGroup and ProblemId nags"() {
        given:
        buildFile getProblemReportingScript("""
            def group = new ProblemGroup() {
                String getName() { "Custom" }
                String getDisplayName() { "Custom" }
                ProblemGroup getParent() { null }
            }
            def id = new ProblemId() {
                String getName() { "Custom problem" }
                String getDisplayName() { "Custom problem" }
                ProblemGroup getGroup() { group }
            }
            println("created: " + id.getName() + " in " + group.getName())
        """)

        when:
        executer.expectDocumentedDeprecationWarning("Subclassing ProblemGroup has been deprecated. This will fail with an error in Gradle 10. Use the predefined groups from Problems.getGroups() instead. $UPGRADE_GUIDE")
        executer.expectDocumentedDeprecationWarning("Subclassing ProblemId has been deprecated. This will fail with an error in Gradle 10. Use the predefined groups from Problems.getGroups() instead. $UPGRADE_GUIDE")
        run("reportProblem")

        then:
        outputContains("created: Custom problem in Custom")
        receivedProblem(0).definition.id.fqid == "Gradle:Deprecation:Subclassing ProblemGroup"
        receivedProblem(1).definition.id.fqid == "Gradle:Deprecation:Subclassing ProblemId"
    }

    def "reporting problems does not nag"() {
        given:
        buildFile getProblemReportingScript("""
            def predefined = problems.groups.others.group("Generic").problemId("label")
            def legacy = ProblemId.create("type", "label", ProblemGroup.create("generic", "Generic"))
            problems.getReporter().report(predefined) {}
            problems.getReporter().report(legacy) {}
        """)

        when:
        run("reportProblem")

        then:
        receivedProblem(0).definition.id.fqid == "Others:Generic:label"
        verifyAll(receivedProblem(1).definition.id) {
            fqid == "generic:type"
            displayName == "label"
        }
    }
}
