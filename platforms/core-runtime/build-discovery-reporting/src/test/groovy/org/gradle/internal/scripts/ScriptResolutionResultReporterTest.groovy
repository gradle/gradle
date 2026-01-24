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

package org.gradle.internal.scripts

import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.fixtures.FakeProblemBuilder
import spock.lang.Specification

class ScriptResolutionResultReporterTest extends Specification {


    def "reportProblem formats single ignored candidate correctly"() {
        given:
        def directory = Mock(File) {
            toString() >> "/some/dir"
        }
        def selectedFile = Mock(File) {
            getName() >> "alice"
        }
        def ignoredFile = Mock(File) {
            getName() >> "bob"
        }
        def result = new ScriptResolutionResult(directory, "script", selectedFile, [ignoredFile])
        def problemReporter = Mock(ProblemReporter)
        def scriptResolutionReporter = new ScriptResolutionResultReporter(problemReporter)

        when:
        scriptResolutionReporter.reportResolutionProblemsOf(result)

        then:
        1 * problemReporter.report(_, _) >> { problemId, configurer ->
            def spec = new FakeProblemBuilder()
            configurer.execute(spec)

            assert problemId.name == "multiple-scripts"
            assert problemId.displayName == "Multiple scripts"
            assert spec.contextualLabel == "Multiple script script files were found in directory '/some/dir'"
            assert spec.details == "Multiple script script files were found in directory '/some/dir'. Selected 'alice', and ignoring 'bob'."
            assert spec.solution == "Delete the files 'bob' in directory '/some/dir'"
        }
    }

    def "reportProblem creates correct problem for multiple ignored candidates"() {
        given:
        def directory = Mock(File) {
            toString() >> "/some/dir"
        }
        def selectedFile = Mock(File) {
            getName() >> "alice"
        }
        def ignoredFile1 = Mock(File) {
            getName() >> "bob"
        }
        def ignoredFile2 = Mock(File) {
            getName() >> "charlie"
        }
        def result = new ScriptResolutionResult(directory, "script", selectedFile, [ignoredFile1, ignoredFile2])
        def problemReporter = Mock(ProblemReporter)
        def scriptResolutionReporter = new ScriptResolutionResultReporter(problemReporter)

        when:
        scriptResolutionReporter.reportResolutionProblemsOf(result)

        then:
        1 * problemReporter.report(_, _) >> { problemId, configurer ->
            def spec = new FakeProblemBuilder()
            configurer.execute(spec)

            assert problemId.name == "multiple-scripts"
            assert problemId.displayName == "Multiple scripts"
            assert spec.contextualLabel == "Multiple script script files were found in directory '/some/dir'"
            assert spec.details == "Multiple script script files were found in directory '/some/dir'. Selected 'alice', and ignoring 'bob', 'charlie'."
            assert spec.solution == "Delete the files 'bob', 'charlie' in directory '/some/dir'"
        }
    }

}
