/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.compile


import org.gradle.api.problems.internal.InternalProblemSpec
import spock.lang.Specification

import javax.tools.Diagnostic
import javax.tools.JavaFileObject

class DiagnosticToProblemListenerTest extends Specification {

    def spec = Mock(InternalProblemSpec) {
        // We report the formatted message in all cases
        1 * additionalData(org.gradle.api.problems.internal.GeneralDataSpec, _)
    }

    def diagnosticToProblemListener = new DiagnosticToProblemListener(null, (fo) -> "Formatted message")

    def "file location is correctly reported"() {
        given:
        def diagnostic = Mock(Diagnostic)
        diagnostic.kind >> Diagnostic.Kind.ERROR
        diagnostic.source >> Mock(JavaFileObject) {
            name >> "SomeFile.java"
        }
        diagnostic.lineNumber | diagnostic.columnNumber | diagnostic.startPosition | diagnostic.endPosition >> Diagnostic.NOPOS

        when:
        diagnosticToProblemListener.buildProblem(diagnostic, spec)

        then:
        1 * spec.fileLocation("SomeFile.java")
        0 * spec.lineInFileLocation(_)
        0 * spec.lineInFileLocation(_, _, _)
        0 * spec.lineInFileLocation(_, _, _, _)
        0 * spec.offsetInFileLocation(_, _, _, _)
    }

    def "file location, and line is correctly reported"() {
        given:
        def diagnostic = Mock(Diagnostic)
        diagnostic.kind >> Diagnostic.Kind.ERROR
        diagnostic.source >> Mock(JavaFileObject) {
            name >> "SomeFile.java"
        }
        diagnostic.lineNumber >> 1
        diagnostic.columnNumber | diagnostic.startPosition | diagnostic.endPosition >> Diagnostic.NOPOS

        when:
        diagnosticToProblemListener.buildProblem(diagnostic, spec)

        then:
        1 * spec.fileLocation("SomeFile.java")
        1 * spec.lineInFileLocation("SomeFile.java", 1)
        0 * spec.lineInFileLocation(_, _, _)
        0 * spec.lineInFileLocation(_, _, _, _)
        0 * spec.offsetInFileLocation(_, _, _, _)
    }


    def "file location, line, and column is correctly reported"() {
        given:
        def diagnostic = Mock(Diagnostic)
        diagnostic.kind >> Diagnostic.Kind.ERROR
        diagnostic.source >> Mock(JavaFileObject) {
            name >> "SomeFile.java"
        }
        diagnostic.lineNumber >> 1
        diagnostic.columnNumber >> 1
        diagnostic.startPosition | diagnostic.endPosition >> Diagnostic.NOPOS

        when:
        diagnosticToProblemListener.buildProblem(diagnostic, spec)

        then:
        1 * spec.fileLocation("SomeFile.java")
        // With a column number, the line-only location should not be reported ...
        0 * spec.lineInFileLocation("SomeFile.java", 1)
        // ... but the line and column location should be
        1 * spec.lineInFileLocation("SomeFile.java", 1, 1)
        0 * spec.lineInFileLocation(_, _, _, _)
        0 * spec.offsetInFileLocation(_, _, _, _)
    }

    def "when only start defined, no offset or slice location is reported"() {
        given:
        def diagnostic = Mock(Diagnostic)
        diagnostic.kind >> Diagnostic.Kind.ERROR
        diagnostic.source >> Mock(JavaFileObject) {
            name >> "SomeFile.java"
        }
        diagnostic.lineNumber >> 1
        diagnostic.columnNumber >> 1
        // Start is defined ...
        diagnostic.startPosition >> 1
        // ... but end is not
        diagnostic.endPosition >> Diagnostic.NOPOS

        when:
        diagnosticToProblemListener.buildProblem(diagnostic, spec)

        then:
        // Behavior should be the same as when only line and column are defined
        1 * spec.fileLocation("SomeFile.java")
        0 * spec.lineInFileLocation(_, _)
        1 * spec.lineInFileLocation("SomeFile.java", 1, 1)
        0 * spec.lineInFileLocation(_, _, _, _)
        0 * spec.offsetInFileLocation(_, _, _, _)
    }

    def "when only the end is defined, no offset or slice location is reported"() {
        given:
        def diagnostic = Mock(Diagnostic)
        diagnostic.kind >> Diagnostic.Kind.ERROR
        diagnostic.source >> Mock(JavaFileObject) {
            name >> "SomeFile.java"
        }
        diagnostic.lineNumber >> 1
        diagnostic.columnNumber >> 1
        // Start is not defined ...
        diagnostic.startPosition >> Diagnostic.NOPOS
        // ... but end is
        diagnostic.endPosition >> 1

        when:
        diagnosticToProblemListener.buildProblem(diagnostic, spec)

        then:
        // Behavior should be the same as when only line and column are defined
        1 * spec.fileLocation("SomeFile.java")
        0 * spec.lineInFileLocation(_, _)
        1 * spec.lineInFileLocation("SomeFile.java", 1, 1)
        0 * spec.lineInFileLocation(_, _, _, _)
        0 * spec.offsetInFileLocation(_, _, _, _)
    }

    def "when both start, position, and end are defined, an offset location is reported"() {
        given:
        def diagnostic = Mock(Diagnostic)
        diagnostic.kind >> Diagnostic.Kind.ERROR
        diagnostic.source >> Mock(JavaFileObject) {
            name >> "SomeFile.java"
        }
        diagnostic.lineNumber >> 1
        diagnostic.columnNumber >> 1
        // Start is defined ...
        diagnostic.startPosition >> 5
        // ... and so is position
        diagnostic.position >> 10
        // ... and so is end
        diagnostic.endPosition >> 20

        when:
        diagnosticToProblemListener.buildProblem(diagnostic, spec)

        then:
        1 * spec.fileLocation("SomeFile.java")
        0 * spec.lineInFileLocation(_, _)
        0 * spec.lineInFileLocation(_, _, _)
        1 * spec.lineInFileLocation("SomeFile.java", 1, 1, 10)
        1 * spec.offsetInFileLocation("SomeFile.java", 10, 10)
    }

}
