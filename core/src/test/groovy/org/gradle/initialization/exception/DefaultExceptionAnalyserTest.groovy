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
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.exceptions.MultiCauseException
import spock.lang.Specification

class DefaultExceptionAnalyserTest extends Specification {

    private final ListenerManager listenerManager = Mock(ListenerManager.class)
    private final StackTraceElement element = new StackTraceElement("class", "method", "filename", 7)
    private final StackTraceElement callerElement = new StackTraceElement("class", "method", "filename", 11)
    private final StackTraceElement otherElement = new StackTraceElement("class", "method", "otherfile", 11)
    private final StackTraceElement elementWithNoSourceFile = new StackTraceElement("class", "method", null, 11)
    private final StackTraceElement elementWithNoLineNumber = new StackTraceElement("class", "method", "filename", -1)
    private final ScriptSource source = Mock(ScriptSource.class)

    def setup() {
        source.getFileName() >> "filename"
        source.getDisplayName() >> "build file filename"
    }

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

    def 'adds location info from deepest stack frame with matching source file and line information'() {
        given:
        def failure = new ContextualException()
        failure.setStackTrace([elementWithNoSourceFile, elementWithNoLineNumber, otherElement, element, callerElement] as StackTraceElement[])
        def analyser = analyser()
        def result = []
        notifyAnalyser(analyser, source)

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.sourceDisplayName == source.displayName
        transformedFailure.lineNumber == 7
    }

    def 'adds location info from deepest cause'() {
        given:
        def cause = new RuntimeException()
        def failure = new ContextualException(new RuntimeException(cause))
        failure.setStackTrace([otherElement, callerElement] as StackTraceElement[])
        cause.setStackTrace([element, otherElement, callerElement] as StackTraceElement[])
        def analyser = analyser()
        def result = []
        notifyAnalyser(analyser, source)

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.sourceDisplayName == source.displayName
        transformedFailure.lineNumber == 7
    }

    def 'does not add location when location cannot be determined'() {
        given:
        def failure = new ContextualException()
        def result = []

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
        notifyAnalyser(analyser, source)

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
        given:
        def failure = new RuntimeException()
        failure.setStackTrace([element, otherElement, callerElement] as StackTraceElement[])
        def analyser = analyser()
        notifyAnalyser(analyser, source)
        def result = []

        when:
        analyser.collectFailures(failure, result)

        then:
        result.size() == 1
        def transformedFailure = result[0]
        transformedFailure instanceof LocationAwareException
        transformedFailure.sourceDisplayName == source.displayName
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
        failure.getStackTrace() >> ([element] as StackTraceElement[])
        return failure
    }

    private void notifyAnalyser(DefaultExceptionAnalyser analyser, final ScriptSource source) {
        analyser.onScriptClassLoaded(source, Script.class)
    }

    private DefaultExceptionAnalyser analyser() {
        1 * listenerManager.addListener(_ as DefaultExceptionAnalyser)
        return new DefaultExceptionAnalyser(listenerManager)
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
