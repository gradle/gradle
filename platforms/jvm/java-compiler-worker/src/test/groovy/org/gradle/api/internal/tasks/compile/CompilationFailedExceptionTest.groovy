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


import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.InternalProblem
import spock.lang.Issue
import spock.lang.Specification

class CompilationFailedExceptionTest extends Specification {

    @Issue('https://github.com/gradle/gradle/issues/31513')
    def "Exception message contains rendered compiler output when compilation problems are available as problem reports"() {
        given:
        def result = Mock(ApiCompilerResult)
        def problem = compilationProblem()
        def diagnosticCounts = "1 error"
        def exception = new CompilationFailedException(result, [problem], diagnosticCounts)

        expect:
        exception.message.normalize() == """Compilation failed; see the compiler output below.
Unknown symbol: foo
1 error"""
    }

    def compilationProblem() {
        Mock(InternalProblem) {
            getDefinition() >> Mock(ProblemDefinition) {
                getId() >> Mock(ProblemId) {
                    getName() >> ''
                    getDisplayName() >> ''
                    getGroup() >> GradleCoreProblemGroup.compilation().java()
                }
            }
            getDetails() >> 'Unknown symbol: foo'
        }
    }
}
