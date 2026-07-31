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

package org.gradle.api.problems.internal

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Severity
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.BuildOperationIdRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

class DefaultProblemReporterTest extends Specification {

    def summarizer = Mock(ProblemSummarizer)

    def "problem is attributed to the build operation provided by the operation id ref"() {
        given:
        def reporter = reporter { new OperationIdentifier(42) }

        when:
        reporter.report(createTestProblem())

        then:
        1 * summarizer.emit(_, new OperationIdentifier(42))
    }

    def "problem is discarded when the operation id ref provides no build operation"() {
        given:
        def reporter = reporter { null }

        when:
        reporter.report(createTestProblem())

        then:
        0 * summarizer.emit(_, _)
    }

    def "discarded problem message renders the problem"() {
        expect:
        with(DefaultProblemReporter.discardedProblemMessage(createTestProblem())) {
            it.startsWith("Discarding problem, no build operation is available to attribute it to on this thread:")
            it.contains("displayName")
            it.contains("generic:message")
        }
    }

    def "problem reported with an explicit operation id is emitted with that id"() {
        given:
        def reporter = reporter { null }
        def problem = createTestProblem()
        def operationId = new OperationIdentifier(1000L)

        when:
        reporter.report(problem, operationId)

        then:
        1 * summarizer.emit(problem, operationId)
    }

    private DefaultProblemReporter reporter(Closure<OperationIdentifier> operationId) {
        new DefaultProblemReporter(
            summarizer,
            operationId as BuildOperationIdRef,
            new ExceptionProblemRegistry(),
            null,
            new ProblemsInfrastructure(
                new AdditionalDataBuilderFactory(),
                Mock(Instantiator),
                Mock(PayloadSerializer),
                Mock(IsolatableFactory),
                Mock(IsolatableToBytesSerializer),
                Mock(ProblemStream)
            )
        )
    }

    private static createTestProblem() {
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
            null
        )
    }
}
