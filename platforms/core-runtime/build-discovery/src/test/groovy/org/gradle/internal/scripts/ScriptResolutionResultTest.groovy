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

class ScriptResolutionResultTest extends Specification {

    def "single candidate"() {
        given:
        def directory = Mock(File)
        def selectedFile = Mock(File)

        when:
        def result = new ScriptResolutionResult(directory, "script", selectedFile, [])

        then:
        result.selectedCandidate == selectedFile
        result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

    def "multiple candidates"() {
        given:
        def directory = Mock(File)
        def selectedFile = Mock(File)
        def ignoredFile1 = Mock(File)
        def ignoredFile2 = Mock(File)

        when:
        def result = new ScriptResolutionResult(directory, "script", selectedFile, [ignoredFile1, ignoredFile2])

        then:
        result.selectedCandidate == selectedFile
        result.scriptFound
        result.ignoredCandidates == [ignoredFile1, ignoredFile2]
    }

    def "no candidates found"() {
        given:
        def directory = Mock(File)

        when:
        def result = new ScriptResolutionResult(directory, "script", null, [])

        then:
        result.selectedCandidate == null
        !result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

    def "ignored candidates list is unmodifiable"() {
        given:
        def directory = Mock(File)
        def selectedFile = Mock(File)
        def ignoredFile = Mock(File)

        when:
        def result = new ScriptResolutionResult(directory, "script", selectedFile, [ignoredFile])
        result.ignoredCandidates.add(Mock(File))

        then:
        thrown(UnsupportedOperationException)
    }

    def "fromSingleFile creates result with no ignored candidates"() {
        given:
        def scriptFile = Mock(File)

        when:
        def result = ScriptResolutionResult.fromSingleFile("script", scriptFile)

        then:
        result.selectedCandidate == scriptFile
        result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

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
        def reporter = Mock(ProblemReporter)

        when:
        result.reportProblem(reporter)

        then:
        1 * reporter.report(_, _) >> { problemId, configurer ->
            def spec = new FakeProblemBuilder()
            configurer.execute(spec)

            assert problemId.name == "multiple-scripts"
            assert problemId.displayName == "Multiple scripts"
            assert spec.contextualLabel == "Multiple script script files were found in directory '/some/dir'"
            assert spec.details.contains("Multiple script script files were found")
            assert spec.details.contains("'alice'")
            assert spec.details.contains("'bob'")
            assert spec.solution.contains("Delete the file")
            assert spec.solution.contains("'bob'")
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
        def reporter = Mock(ProblemReporter)

        when:
        result.reportProblem(reporter)

        then:
        1 * reporter.report(_, _) >> { problemId, configurer ->
            def spec = new FakeProblemBuilder()
            configurer.execute(spec)

            assert problemId.name == "multiple-scripts"
            assert problemId.displayName == "Multiple scripts"
            assert spec.contextualLabel == "Multiple script script files were found in directory '/some/dir'"
            assert spec.details.contains("Multiple script script files were found")
            assert spec.details.contains("'alice'")
            assert spec.details.contains("'bob'")
            assert spec.details.contains("'charlie'")
            assert spec.solution.contains("Delete the files")
            assert spec.solution.contains("'bob', 'charlie'")
        }
    }

    def "fromSingleFile works with non-null arguments"() {
        given:
        def scriptFile = Mock(File)

        when:
        def result = ScriptResolutionResult.fromSingleFile("script", scriptFile)

        then:
        result.selectedCandidate == scriptFile
        result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

    def "fromSingleFile throws NPE when basename is null"() {
        given:
        def scriptFile = Mock(File)

        when:
        ScriptResolutionResult.fromSingleFile(null, scriptFile)

        then:
        thrown(NullPointerException)
    }

    def "fromSingleFile throws NPE when scriptFile is null"() {
        when:
        ScriptResolutionResult.fromSingleFile("script", null)

        then:
        thrown(NullPointerException)
    }

}
