/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal

import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.Severity
import org.gradle.api.problems.SharedProblemGroup
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Specification

class DefaultProblemTest extends Specification {
    def "unbound builder result is equal to original"() {
        def additionalData = Mock(AdditionalData)
        def problem = createTestProblem(severity, additionalData)

        def newProblem = problem.toBuilder().build()
        expect:
        newProblem.definition.id.name == problem.definition.id.name
        newProblem.definition.id.displayName == problem.definition.id.displayName
        newProblem.definition.severity == problem.definition.severity
        newProblem.solutions == problem.solutions
        newProblem.additionalData == problem.additionalData
        newProblem.details == problem.details
        newProblem.exception == problem.exception
        newProblem.originLocations == problem.originLocations

        newProblem == problem

        where:
        severity << [Severity.WARNING, Severity.ERROR]
    }

    def "unbound builder result with modified #changedAspect is not equal"() {
        def problem = createTestProblem()


        when:
        def builder = problem.toBuilder()
        changeClosure.curry(builder).run()
        def newProblem = builder.build()

        then:

        newProblem != problem

        where:
        changedAspect | changeClosure
        "severity"    | { it.severity(Severity.WARNING) }
        "locations"   | { it.fileLocation("file") }
        "details"     | { it.details("details") }
    }


    def "unbound builder result with a change and check report"() {
        given:
        def emitter = Mock(ProblemSummarizer)
        def problemReporter = new DefaultProblemReporter(emitter, null, CurrentBuildOperationRef.instance(), new DefaultAdditionalDataBuilderFactory(), new ExceptionProblemRegistry(), null)
        def problem = createTestProblem(Severity.WARNING)
        def builder = problem.toBuilder()
        def newProblem = builder
            .solution("solution")
            .build()
        def operationId = new OperationIdentifier(1000L)

        when:
        problemReporter.report(newProblem, operationId)

        then:
        // We are not running this test as an integration test, so we won't have a BuildOperationId available,
        // i.e. the OperationId will be null
        1 * emitter.emit(newProblem, operationId)
        newProblem.definition.id.name == problem.definition.id.name
        newProblem.definition.id.displayName == problem.definition.id.displayName
        newProblem.additionalData == problem.additionalData
        newProblem.details == problem.details
        newProblem.exception == problem.exception
        newProblem.originLocations == problem.originLocations
        newProblem.definition.severity == problem.definition.severity
        newProblem.solutions == ["solution"]
        newProblem.class == DefaultProblem
    }

    private static createTestProblem(Severity severity = Severity.ERROR, AdditionalData additionalData = null) {
        new DefaultProblem(
            new DefaultProblemDefinition(
                new DefaultProblemId('message', "displayName", SharedProblemGroup.generic()),
                severity,
                Documentation.userManual('id'),
            ),
            null,
            [],
            [],
            [],
            'description',
            new RuntimeException('cause'),
            additionalData
        )
    }

    def "unbound basic builder result is DefaultProblem"() {
        given:
        def problem = new DefaultProblem(
            new DefaultProblemDefinition(
                new DefaultProblemId('message', "displayName", SharedProblemGroup.generic()),
                Severity.WARNING,
                Documentation.userManual('id'),
            ),
            'contextual label',
            ['contextual solution'],
            [],
            [],
            'description',
            new RuntimeException('cause'),
            null
        )

        when:
        def newProblem = problem.toBuilder().build()

        then:
        newProblem.class == DefaultProblem
    }
}
