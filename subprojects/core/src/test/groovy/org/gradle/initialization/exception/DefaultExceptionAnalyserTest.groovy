/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.initialization.exception

import org.gradle.api.GradleScriptException
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.Describables
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.problems.Location
import org.gradle.problems.buildtree.ProblemLocationAnalyzer
import spock.lang.Specification

class DefaultExceptionAnalyserTest extends Specification {
    private final ProblemLocationAnalyzer locationAnalyzer = Stub(ProblemLocationAnalyzer)
    private final StackTraceElement element1 = new StackTraceElement("class", "method", "filename", 7)
    private final StackTraceElement element2 = new StackTraceElement("class", "method", "filename", 11)
    private final StackTraceElement element3 = new StackTraceElement("class", "method", "otherfile", 11)

    def 'wraps original exception when it is not a contextual exception'() {
        given:
        def failure = new RuntimeException()
        def analyser = analyser()
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.cause.is(failure)
        transformedFailure.reportableCauses.isEmpty()
    }

    def 'wraps contextual exception with location aware exception'() {
        given:
        def failure = new ContextualException()
        def analyser = analyser()
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException

        transformedFailure.cause.is(failure)
        transformedFailure.reportableCauses.isEmpty()
    }

    def 'wraps highest contextual exception with location aware exception'() {
        given:
        def cause = new ContextualException()
        def failure = new ContextualException(cause)
        def analyser = analyser()
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.cause.is(failure)
        transformedFailure.reportableCauses == [cause]
    }

    def 'adds location info from stack trace'() {
        def failure = new ContextualException()
        def stackTrace = [element2, element1]
        failure.setStackTrace(stackTrace as StackTraceElement[])
        def analyser = analyser()
        def result = []

        given:
        _ * locationAnalyzer.locationForUsage(stackTrace, true) >> location("<source>", 7)

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.sourceDisplayName == "<source>"
        transformedFailure.lineNumber == 7
    }

    def 'adds location info from deepest cause'() {
        def cause = new RuntimeException()
        def failure = new ContextualException(new RuntimeException(cause))

        def stackTrace1 = [element3, element2]
        failure.setStackTrace(stackTrace1 as StackTraceElement[])

        def stackTrace2 = [element3, element2, element1]
        cause.setStackTrace(stackTrace2 as StackTraceElement[])

        def analyser = analyser()
        def result = []

        given:
        _ * locationAnalyzer.locationForUsage(stackTrace1, true) >> location("<source>", 12)
        _ * locationAnalyzer.locationForUsage(stackTrace2, true) >> location("<source>", 7)

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.sourceDisplayName == "<source>"
        transformedFailure.lineNumber == 7
    }

    def 'does not add location when location cannot be determined'() {
        def failure = new ContextualException()
        def result = []

        given:
        _ * locationAnalyzer.locationForUsage(_, _) >> null

        when:
        analyser().collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.sourceDisplayName ==  null
        transformedFailure.lineNumber == null
    }

    def 'wraps contextual multi cause exception with location aware exception'() {
        given:
        def cause1 = new ContextualException()
        def cause2 = new ContextualException()
        def failure = new ContextualMultiCauseException(cause1, cause2)
        def result = []

        when:
        analyser().collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.cause.is(failure)
        transformedFailure.reportableCauses == [cause1, cause2]
    }

    def 'uses original exception when it is already location aware'() {
        given:
        def failure = locationAwareException(null)
        def analyser = analyser()
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure.is(failure)
    }

    def 'uses deepest ScriptException exception'() {
        given:
        def cause = new GradleScriptException("broken", new RuntimeException())
        def failure = new GradleScriptException("broken", new RuntimeException(cause))
        def result = []

        when:
        analyser().collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.cause.is(cause)
    }

    def 'uses deepest location aware exception'() {
        given:
        def cause = locationAwareException(null)
        def failure = locationAwareException(new RuntimeException(cause))
        def result = []

        when:
        analyser().collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure.is(cause)
    }

    def 'prefers script exception over contextual exception'() {
        given:
        def cause = new GradleScriptException("broken", new ContextualException())
        def failure = new TaskExecutionException(null, cause)
        def result = []

        when:
        analyser().collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.cause.is(cause)
    }

    def 'prefers location aware exception over script exception'() {
        given:
        def cause = locationAwareException(new GradleScriptException("broken", new RuntimeException()))
        def failure = new TaskExecutionException(null, cause)
        def result = []

        expect:
        analyser().collectFailures(failure, result)
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
    }

    def 'wraps arbitrary failure with location information'() {
        def failure = new RuntimeException()
        def stackTrace = [element1, element3, element2]
        failure.setStackTrace(stackTrace as StackTraceElement[])
        def analyser = analyser()
        def result = []

        given:
        _ * locationAnalyzer.locationForUsage(stackTrace, true) >> location("<source>", 7)

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.sourceDisplayName == "<source>"
        transformedFailure.lineNumber == 7
        transformedFailure.cause.is(failure)
    }

    def 'unpacks project configuration exception with script execution cause'() {
        given:
        def scriptFailure = new GradleScriptException("broken", new RuntimeException())
        def failure = new ProjectConfigurationException("broken", scriptFailure)
        def analyser = analyser()
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.cause.is(scriptFailure)
    }

    def 'unpacks project configuration exception with other cause'() {
        given:
        def otherFailure = new RuntimeException("broken")
        def failure = new ProjectConfigurationException("broken", otherFailure)
        def analyser = analyser()
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.cause.is(failure)
    }

    def 'unpacks project configuration exception with multiple causes'() {
        given:
        def scriptFailure = new GradleScriptException("broken", new RuntimeException())
        def otherFailure1 = new RuntimeException("broken")
        def otherFailure2 = new RuntimeException("broken")
        def failure = new ProjectConfigurationException("broken", [scriptFailure, otherFailure1, otherFailure2])
        def analyser = analyser()
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 2

        def transformed1 = result[0]
        transformed1 instanceof LocationAwareException
        transformed1.cause.is(scriptFailure)

        def transformed2 = result[1]
        transformed2 instanceof LocationAwareException
        transformed2.cause.is(failure)

        failure.causes == [otherFailure1, otherFailure2]
    }

    private Throwable locationAwareException(final Throwable cause) {
        final Throwable failure = Mock(TestException.class)
        failure.getCause() >> cause
        failure.getStackTrace() >> ([element1] as StackTraceElement[])
        return failure
    }

    private Location location(String longDisplayName, int line) {
        return new Location(Describables.of(longDisplayName), Describables.of("short"), line)
    }

    private DefaultExceptionAnalyser analyser() {
        return new DefaultExceptionAnalyser(locationAnalyzer)
    }

    @Contextual
    static class ContextualException extends RuntimeException {
        ContextualException() {
            super("failed")
        }

        ContextualException(Throwable throwable) {
            super(throwable)
        }
    }

    @Contextual
    static class ContextualMultiCauseException extends RuntimeException implements MultiCauseException {
        private List<Throwable> causes

        ContextualMultiCauseException(Throwable... throwables) {
            this.causes = Arrays.asList(throwables)
        }

        List<? extends Throwable> getCauses() {
            return causes
        }
    }

    @Contextual
    abstract static class TestException extends LocationAwareException {
        protected TestException(Throwable cause, ScriptSource source, Integer lineNumber) {
            super(cause, source, lineNumber)
        }
    }
}
