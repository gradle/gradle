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
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Severity
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

class DefaultProblemTest extends Specification {
    def "unbound builder result is equal to original"() {
        def additionalData = Mock(AdditionalData)
        def problem = createTestProblem(additionalData)
        def newProblem = toBuilder(problem).build()

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
    }

    def ProblemBuilderInternal toBuilder(DefaultProblem problem) {
        problem.toBuilder(new ProblemsInfrastructure(new AdditionalDataBuilderFactory(), Mock(Instantiator), Mock(PayloadSerializer), Mock(IsolatableFactory), Mock(IsolatableToBytesSerializer), Mock(ProblemStream), null))
    }

    def "unbound builder result with modified #changedAspect is not equal"() {
        def problem = createTestProblem()


        when:
        def builder = toBuilder(problem)
        changeClosure.curry(builder).run()
        def newProblem = builder.build()

        then:

        newProblem != problem

        where:
        changedAspect | changeClosure
        "locations"   | { it.fileLocation("file") }
        "details"     | { it.details("details") }
    }


    private static createTestProblem(AdditionalData additionalData = null) {
        new DefaultProblem(
            new DefaultProblemDefinition(
                ProblemId.create('message', "displayName", ProblemGroup.create("generic", "Generic")),
                Severity.ERROR,
                Documentation.userManual('id'),
            ),
            null,
            [],
            [],
            [],
            'description',
            new RuntimeException('cause'),
            additionalData,
            false
        )
    }

    def "unbound basic builder result is DefaultProblem"() {
        given:
        def problem = new DefaultProblem(
            new DefaultProblemDefinition(
                ProblemId.create('message', "displayName", ProblemGroup.create("generic", "Generic")),
                Severity.WARNING,
                Documentation.userManual('id'),
            ),
            'contextual label',
            ['contextual solution'],
            [],
            [],
            'description',
            new RuntimeException('cause'),
            null,
            false


        )

        when:
        def newProblem = toBuilder(problem).build()

        then:
        newProblem.class == DefaultProblem
    }

    def "problems whose origin traces differ in an authored frame are different problems"() {
        expect:
        problemWithOrigin(field("a"), task(":x")) != problemWithOrigin(field("b"), task(":x"))
        problemWithOrigin(field("a"), task(":x")).hashCode() != problemWithOrigin(field("b"), task(":x")).hashCode()
    }

    def "problems whose origin traces differ only in build model frames or in locality are the same problem"() {
        expect:
        problemWithOrigin(field("a"), task(":x")) == problemWithOrigin(field("a"), task(":y"))
        problemWithOrigin(field("a"), task(":x")).hashCode() == problemWithOrigin(field("a"), task(":y")).hashCode()
        problemWithOrigin(new org.gradle.api.problems.internal.location.PropertyFrame("field", "a", org.gradle.internal.code.Locality.LOCAL)) == problemWithOrigin(field("a"))
    }

    private static org.gradle.api.problems.internal.location.Frame field(String name) {
        new org.gradle.api.problems.internal.location.PropertyFrame("field", name, org.gradle.internal.code.Locality.UNKNOWN)
    }

    private static org.gradle.api.problems.internal.location.Frame task(String path) {
        new org.gradle.api.problems.internal.location.TaskFrame(path, org.gradle.internal.code.Locality.UNKNOWN)
    }

    private static ProblemInternal problemWithOrigin(org.gradle.api.problems.internal.location.Frame... frames) {
        def group = org.gradle.api.problems.ProblemGroup.create("g", "G")
        def definition = new DefaultProblemDefinition(new DefaultProblemId("p", "P", group), org.gradle.api.problems.Severity.WARNING, null)
        new DefaultProblem(definition, "label", [], [], []).withOriginTraces([new org.gradle.api.problems.internal.location.Trace(frames.toList())])
    }
}
