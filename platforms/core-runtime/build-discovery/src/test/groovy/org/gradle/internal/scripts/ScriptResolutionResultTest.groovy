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
