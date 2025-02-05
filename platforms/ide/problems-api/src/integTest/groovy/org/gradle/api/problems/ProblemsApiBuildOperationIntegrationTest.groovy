/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.collect.Iterables
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.operations.problems.ProblemUsageProgressDetails
import org.gradle.util.internal.TextUtil

import static org.gradle.api.problems.fixtures.ReportingScript.getProblemReportingScript

class ProblemsApiBuildOperationIntegrationTest extends AbstractIntegrationSpec {
    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportingScript(taskActionMethodBody)
    }

    def "can emit a problem with minimal configuration"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {}
        """

        when:
        executer.withArgument("--no-problems-report")
        run("reportProblem")

        then:
        def problem = Iterables.getOnlyElement(buildOperations.progress(ProblemUsageProgressDetails)).details
        with(problem) {
            with(definition) {
                name == 'type'
                displayName == 'label'
                with(group) {
                    displayName == 'group label'
                    name == 'generic'
                    parent == null
                }
                documentationLink == null
            }
            severity == Severity.WARNING.name()
            contextualLabel == null
            solutions == []
            details == null
            originLocations.size() == 1
            with(originLocations[0]) {
                path == this.buildFile.absolutePath
                line == 13
                column == null
                length == null
            }
            contextualLocations.size() == 1
            with(contextualLocations[0]) {
                path == ':reportProblem'
                buildPath == ':'
            }
            failure == null
        }
    }

    def "can emit a problem with stack location"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.stackLocation()
            }
        """

        when:
        run('reportProblem')

        then:
        def problem = Iterables.getOnlyElement(buildOperations.progress(ProblemUsageProgressDetails)).details
        with(problem) {
            with(definition) {
                name == 'type'
                displayName == 'label'
                with(group) {
                    displayName == 'group label'
                    name == 'generic'
                    parent == null
                }
                documentationLink == null
            }
            severity == Severity.WARNING.name()
            contextualLabel == null
            solutions == []
            details == null
            originLocations.size() == 1
            with(originLocations[0]) {
                path == this.buildFile.absolutePath
                line == 13
                column == null
                length == null
            }
            contextualLocations.size() == 1
            with(contextualLocations[0]) {
                path == ':reportProblem'
                buildPath == ':'
            }
            // TODO: IMO the failure should be null...
            failure != null
        }
    }

    def "can emit a problem with all fields"() {
        given:
        def location0 = file('src/main/java/SourceFile0.java').absolutePath
        def location1 = file('src/main/java/SourceFile1.java').absolutePath
        def location2 = file('src/main/java/SourceFile2.java').absolutePath
        def location3 = file('src/main/java/SourceFile3.java').absolutePath
        def location4 = file('src/main/java/SourceFile4.java').absolutePath

        withReportProblemTask """
            ${ProblemGroup.name} problemGroupParent = ${ProblemGroup.name}.create("parent", "parent group label");
            ${ProblemGroup.name} problemGroup = ${ProblemGroup.name}.create("problem group", "problem group label", problemGroupParent);
            ${ProblemId.name} problemId = ${ProblemId.name}.create("type", "label", problemGroup)
            problems.getReporter().report(problemId) {
                it.contextualLabel("contextual label")
                it.documentedAt("https://example.org/doc")
                it.fileLocation("${TextUtil.escapeString(location0)}")
                it.lineInFileLocation("${TextUtil.escapeString(location1)}", 25)
                it.lineInFileLocation("${TextUtil.escapeString(location2)}", 35, 4)
                it.lineInFileLocation("${TextUtil.escapeString(location3)}", 45, 7, 10)
                it.offsetInFileLocation("${TextUtil.escapeString(location4)}", 55, 20)
                it.stackLocation()
                it.details("problem details")
                it.solution("solution 1")
                it.solution("solution 2")
                it.severity(Severity.ERROR)
                it.withException(new IllegalArgumentException("problem exception"))
            }
        """

        when:
        run('reportProblem')

        then:
        def problem = Iterables.getOnlyElement(buildOperations.progress(ProblemUsageProgressDetails)).details
        with(problem) {
            with(definition) {
                name == 'type'
                displayName == 'label'
                with(group) {
                    displayName == 'problem group label'
                    name == 'problem group'
                    with(parent) {
                        displayName == 'parent group label'
                        name == 'parent'
                        parent == null
                    }
                }
                with(documentationLink) {
                    url == 'https://example.org/doc'
                }
            }
            severity == Severity.ERROR.name()
            contextualLabel == 'contextual label'
            solutions == ['solution 1', 'solution 2']
            details == 'problem details'
            originLocations.size() == 6
            with(originLocations[0]) {
                path == location0
                !containsKey('line')
            }
            with(originLocations[1]) {
                path == location1
                line == 25
                column == null
                length == null
            }
            with(originLocations[2]) {
                path == location2
                line == 35
                column == 4
                length == null
            }
            with(originLocations[3]) {
                path == location3
                line == 45
                column == 7
                length == 10
            }
            with(originLocations[4]) {
                path == location4
                offset == 55
                length == 20
            }
            contextualLocations.size() == 1
            with(contextualLocations[0]) {
                path == ':reportProblem'
                buildPath == ':'
            }
            with(failure) {
                message == 'problem exception'
                stackTrace.startsWith('java.lang.IllegalArgumentException: problem exception')
            }
        }
    }

    static String problemIdScript() {
        """${ProblemGroup.name} problemGroup = ${ProblemGroup.name}.create("generic", "group label");
           ${ProblemId.name} problemId = ${ProblemId.name}.create("type", "label", problemGroup)"""
    }
}
