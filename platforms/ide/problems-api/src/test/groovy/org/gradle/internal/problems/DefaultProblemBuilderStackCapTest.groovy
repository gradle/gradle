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

package org.gradle.internal.problems

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultProblemBuilder
import org.gradle.api.problems.internal.IsolatableToBytesSerializer
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemsInfrastructure
import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.problems.failure.DefaultFailureFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

/**
 * Characterizes which problems still collect a stack once the capture cap is spent.
 *
 * <p>An ERROR-severity problem without its own exception gets a synthetic one, routing it through
 * {@link ProblemStream#forThrownException} and so past the cap that stops every other problem.</p>
 */
class DefaultProblemBuilderStackCapTest extends Specification {

    private static final int FULL_BUDGET = 2

    def locationAnalyzer = Mock(ProblemLocationAnalyzer)
    def userCodeContext = Mock(UserCodeApplicationContext)
    def boundedCallerStackCapturer = Mock(BoundedCallerStackCapturer)

    def problemGroup = ProblemGroup.create("group", "label")
    def problemId = ProblemId.create("id", "Problem Id", problemGroup)

    def problemStream = new DefaultProblemDiagnosticsFactory(
        DefaultFailureFactory.withDefaultClassifier(),
        locationAnalyzer,
        userCodeContext,
        FULL_BUDGET,
        0,
        boundedCallerStackCapturer
    ).newStream()

    def "error severity keeps collecting stacks past the cap"() {
        given:
        exhaustFullBudget()

        when:
        def problem = buildProblemWithSeverity(Severity.ERROR)

        then:
        !stackOf(problem).empty
    }

    def "warning severity stops collecting stacks past the cap"() {
        given:
        exhaustFullBudget()

        when:
        def problem = buildProblemWithSeverity(Severity.WARNING)

        then:
        stackOf(problem).empty
    }

    def "unset severity stops collecting stacks past the cap"() {
        given:
        exhaustFullBudget()

        when:
        def problem = createProblemBuilder().id(problemId).stackLocation().build()

        then:
        problem.definition.severity == Severity.WARNING
        stackOf(problem).empty
    }

    def "#severity severity collects a stack while the cap allows it"() {
        when:
        def problem = buildProblemWithSeverity(severity)

        then:
        !stackOf(problem).empty

        where:
        severity << [Severity.ERROR, Severity.WARNING]
    }

    def "a caller-supplied exception is kept past the cap regardless of severity"() {
        given:
        exhaustFullBudget()
        def failure = new RuntimeException("boom")

        when:
        def problem = createProblemBuilder()
            .id(problemId)
            .internalSeverity(Severity.WARNING)
            .stackLocation()
            .withException(failure)
            .build()

        then:
        problem.exception.is(failure)
        !stackOf(problem).empty
    }

    def "error severity does not surface its synthetic exception on the problem"() {
        when:
        def problem = buildProblemWithSeverity(Severity.ERROR)

        then:
        !stackOf(problem).empty
        problem.exception == null
    }

    private void exhaustFullBudget() {
        FULL_BUDGET.times { problemStream.forCurrentCaller() }
    }

    private ProblemInternal buildProblemWithSeverity(Severity severity) {
        createProblemBuilder()
            .id(problemId)
            .internalSeverity(severity)
            .stackLocation()
            .build()
    }

    private static List<StackTraceElement> stackOf(ProblemInternal problem) {
        def stackLocations = (problem.originLocations + problem.contextualLocations).findAll { it instanceof StackTraceLocation }
        stackLocations.collectMany { (it as StackTraceLocation).stackTrace }
    }

    private DefaultProblemBuilder createProblemBuilder() {
        new DefaultProblemBuilder(new ProblemsInfrastructure(
            new AdditionalDataBuilderFactory(),
            Mock(Instantiator),
            Mock(PayloadSerializer),
            Mock(IsolatableFactory),
            Mock(IsolatableToBytesSerializer),
            problemStream
        ))
    }
}
