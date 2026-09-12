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

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import spock.lang.Issue

import static org.gradle.api.problems.fixtures.ReportingScript.getProblemReportingScript

@Issue("https://github.com/gradle/gradle/issues/38670")
class PredefinedProblemGroupsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportingScript(taskActionMethodBody)
    }

    def "can report a problem into a predefined sub-group"() {
        given:
        withReportProblemTask """
            problems.reporter.report(problems.groups.compilation.java.problem("Unused import")) {}
        """

        when:
        run("reportProblem")

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == "Compilation:Java:Unused import"
            definition.id.name == "Unused import"
            definition.id.displayName == "Unused import"
            definition.id.group.name == "Java"
            definition.id.group.displayName == "Java"
            definition.id.group.description == "Java code compilation, including the compiler's configuration, compiler invocation, or compiler plugins."
            definition.id.group.parent.name == "Compilation"
            definition.id.group.parent.description == "Code compilation, including the compiler's configuration, compiler invocation, or compiler plugins."
            definition.id.group.parent.parent == null
        }
    }

    def "can report a problem into a sub-group of the closed Gradle root group"() {
        given:
        withReportProblemTask """
            problems.reporter.report(problems.groups.gradle.deprecation.problem("Deprecated feature used")) {}
        """

        when:
        run("reportProblem")

        then:
        receivedProblem.definition.id.fqid == "Gradle:Deprecation:Deprecated feature used"
    }

    def "can report a problem into user-defined sub-groups"() {
        given:
        withReportProblemTask """
            def kmp = problems.groups.transformation.group("KMP")
            problems.reporter.report(kmp.group("JavaScript").problem("Bundle failed")) {}
            problems.reporter.report(kmp.problem("Compilation failed")) {}
        """

        when:
        run("reportProblem")

        then:
        def bundleFailed = findReceivedProblem { it.definition.id.fqid == "Transformation:KMP:JavaScript:Bundle failed" }
        bundleFailed.definition.id.group.description == null
        bundleFailed.definition.id.group.parent.description == null
        bundleFailed.definition.id.group.parent.parent.description == "Generation or manipulation of code, binaries, or resources before running or packaging."
        findReceivedProblem { it.definition.id.fqid == "Transformation:KMP:Compilation failed" }
    }

    def "can report a problem into Undefined groups"() {
        given:
        withReportProblemTask """
            problems.reporter.report(problems.groups.others.undefined.problem("Something odd")) {}
            problems.reporter.report(problems.groups.compilation.undefined.problem("Unknown compiler")) {}
            problems.reporter.report(problems.groups.compilation.java.undefined.problem("Unknown compiler")) {}
        """

        when:
        run("reportProblem")

        then:
        findReceivedProblem { it.definition.id.fqid == "Others:Undefined:Something odd" }.definition.id.group.description == "Problems without an explicitly defined Others sub-group"
        findReceivedProblem { it.definition.id.fqid == "Compilation:Undefined:Unknown compiler" }.definition.id.group.description == "Problems without an explicitly defined Compilation sub-group"
        findReceivedProblem { it.definition.id.fqid == "Compilation:Java:Undefined:Unknown compiler" }.definition.id.group.description == "Problems without an explicitly defined Java sub-group"
    }

    def "looking up a predefined sub-group by name returns the predefined group"() {
        given:
        withReportProblemTask """
            def java = problems.groups.compilation.group("Java")
            println("same instance: " + java.is(problems.groups.compilation.java))
            println("description: " + java.description)
            problems.reporter.report(java.problem("Unused import")) {}
        """

        when:
        run("reportProblem")

        then:
        outputContains("same instance: true")
        outputContains("description: Java code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.")
        receivedProblem.definition.id.fqid == "Compilation:Java:Unused import"
    }

    def "problems from predefined groups are rendered on the console with their group chain"() {
        given:
        withReportProblemTask """
            problems.reporter.report(problems.groups.compilation.java.problem("Unused import")) {
                it.contextualLabel("Import of java.util.List is not used")
            }
        """

        when:
        executer.withWarningMode(WarningMode.All)
        run("reportProblem")

        then:
        outputContains("Problem found: Unused import (in Compilation > Java)")
        receivedProblem.definition.id.fqid == "Compilation:Java:Unused import"
    }

    def "creating a group whose name differs only by case from a predefined group creates a distinct user group"() {
        given:
        withReportProblemTask """
            problems.reporter.report(problems.groups.compilation.group("java").problem("Unused import")) {}
        """

        when:
        run("reportProblem")

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == "Compilation:java:Unused import"
            definition.id.group.description == null
        }
    }

    def "predefined groups can be used from a Kotlin DSL build script"() {
        given:
        buildKotlinFile """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class ProblemReportingTask @Inject constructor(private val problems: Problems) : DefaultTask() {
                @TaskAction
                fun run() {
                    problems.reporter.report(problems.groups.compilation.kotlin.problem("Unused import")) {}
                    problems.reporter.report(problems.groups.transformation.group("KMP").problem("Compilation failed")) {}
                }
            }

            tasks.register<ProblemReportingTask>("reportProblem")
        """

        when:
        run("reportProblem")

        then:
        findReceivedProblem { it.definition.id.fqid == "Compilation:Kotlin:Unused import" }
        findReceivedProblem { it.definition.id.fqid == "Transformation:KMP:Compilation failed" }
    }
}
