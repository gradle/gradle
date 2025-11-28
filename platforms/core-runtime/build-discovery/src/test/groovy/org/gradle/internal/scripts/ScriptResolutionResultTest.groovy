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

    def "can create result with selected candidate and no ignored candidates"() {
        given:
        def directory = new File("/some/dir")
        def selectedFile = new File(directory, "build.gradle")

        when:
        def result = new ScriptResolutionResult(directory, "build", selectedFile, [])

        then:
        result.selectedCandidate == selectedFile
        result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

    def "can create result with selected candidate and ignored candidates"() {
        given:
        def directory = new File("/some/dir")
        def selectedFile = new File(directory, "build.gradle")
        def ignoredFile1 = new File(directory, "build.gradle.kts")
        def ignoredFile2 = new File(directory, "build.gradle.dcl")

        when:
        def result = new ScriptResolutionResult(directory, "build", selectedFile, [ignoredFile1, ignoredFile2])

        then:
        result.selectedCandidate == selectedFile
        result.scriptFound
        result.ignoredCandidates == [ignoredFile1, ignoredFile2]
    }

    def "can create result with no selected candidate"() {
        given:
        def directory = new File("/some/dir")

        when:
        def result = new ScriptResolutionResult(directory, "build", null, [])

        then:
        result.selectedCandidate == null
        !result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

    def "ignored candidates list is immutable"() {
        given:
        def directory = new File("/some/dir")
        def selectedFile = new File(directory, "build.gradle")
        def ignoredFile = new File(directory, "build.gradle.kts")

        when:
        def result = new ScriptResolutionResult(directory, "build", selectedFile, [ignoredFile])
        result.ignoredCandidates.add(new File(directory, "another.gradle"))

        then:
        thrown(UnsupportedOperationException)
    }

    def "fromSingleFile creates result with no ignored candidates"() {
        given:
        def scriptFile = new File("/some/dir/settings.gradle")

        when:
        def result = ScriptResolutionResult.fromSingleFile("settings", scriptFile)

        then:
        result.selectedCandidate == scriptFile
        result.scriptFound
        result.ignoredCandidates.isEmpty()
    }


    def "reportProblem reports when both selected and ignored candidates exist"() {
        given:
        def directory = new File("/some/dir")
        def selectedFile = new File(directory, "build.gradle")
        def ignoredFile1 = new File(directory, "build.gradle.kts")
        def ignoredFile2 = new File(directory, "build.gradle.dcl")
        def result = new ScriptResolutionResult(directory, "build", selectedFile, [ignoredFile1, ignoredFile2])
        def reporter = Mock(ProblemReporter)

        when:
        result.reportProblem(reporter)

        then:
        1 * reporter.report(_, _) >> { problemId, configurer ->
            def spec = new FakeProblemBuilder()
            configurer.execute(spec)

            assert problemId.name == "multiple-scripts"
            assert problemId.displayName == "Multiple scripts"
            assert spec.contextualLabel == "Multiple build script files were found in directory '/some/dir'"
            assert spec.details.contains("Multiple build script files were found")
            assert spec.details.contains("'build.gradle'")
            assert spec.details.contains("'build.gradle.kts'")
            assert spec.details.contains("'build.gradle.dcl'")
            assert spec.solution.contains("Delete the files")
            assert spec.solution.contains("'build.gradle.kts', 'build.gradle.dcl'")
        }
    }

    def "reportProblem formats single ignored candidate correctly"() {
        given:
        def directory = new File("/some/dir")
        def selectedFile = new File(directory, "settings.gradle")
        def ignoredFile = new File(directory, "settings.gradle.kts")
        def result = new ScriptResolutionResult(directory, "settings", selectedFile, [ignoredFile])
        def reporter = Mock(ProblemReporter)

        when:
        result.reportProblem(reporter)

        then:
        1 * reporter.report(_, _)
    }

    def "fromSingleFile with file creates result with selected candidate and no ignored candidates"() {
        given:
        def scriptFile = new File("/some/dir/build.gradle")

        when:
        def result = ScriptResolutionResult.fromSingleFile("build", scriptFile)

        then:
        result.selectedCandidate == scriptFile
        result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

    def "fromSingleFile with null file creates result with no selected candidate"() {
        when:
        def result = ScriptResolutionResult.fromSingleFile("build", null)

        then:
        result.selectedCandidate == null
        !result.scriptFound
        result.ignoredCandidates.isEmpty()
    }

}
