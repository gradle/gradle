/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.initialization

import org.gradle.api.GradleScriptException
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
        Throwable failure = new RuntimeException()
        DefaultExceptionAnalyser analyser = analyser()

        when:
        Throwable transformed = analyser.transform(failure)

        then:
        transformed instanceof LocationAwareException

        LocationAwareException gse = transformed
        gse.cause.is(failure)
        gse.reportableCauses.isEmpty()
    }

    def 'wraps contextual exception with location aware exception'() {
        given:
        Throwable failure = new ContextualException()
        DefaultExceptionAnalyser analyser = analyser()

        when:
        Throwable transformedFailure = analyser.transform(failure)

        then:
        transformedFailure instanceof LocationAwareException

        LocationAwareException gse = transformedFailure
        gse.cause.is(failure)
        gse.reportableCauses.isEmpty()
    }

    def 'wraps highest contextual exception with location aware exception'() {
        given:
        Throwable cause = new ContextualException()
        Throwable failure = new ContextualException(cause)
        DefaultExceptionAnalyser analyser = analyser()

        when:
        Throwable transformedFailure = analyser.transform(failure)

        then:
        LocationAwareException gse = transformedFailure
        gse.cause.is(failure)
        gse.reportableCauses == [cause]
    }

    def 'adds location info from deepest stack frame with matching source file and line information'() {
        given:
        Throwable failure = new ContextualException()
        failure.setStackTrace([elementWithNoSourceFile, elementWithNoLineNumber, otherElement, element, callerElement] as StackTraceElement[])
        DefaultExceptionAnalyser analyser = analyser()

        when:
        notifyAnalyser(analyser, source)
        Throwable transformedFailure = analyser.transform(failure)

        then:
        LocationAwareException gse = (LocationAwareException) transformedFailure
        gse.sourceDisplayName == source.displayName
        gse.lineNumber == 7
    }

    def 'adds location info from deepest cause'() {
        given:
        RuntimeException cause = new RuntimeException()
        ContextualException failure = new ContextualException(new RuntimeException(cause))
        failure.setStackTrace([otherElement, callerElement] as StackTraceElement[])
        cause.setStackTrace([element, otherElement, callerElement] as StackTraceElement[])
        DefaultExceptionAnalyser analyser = analyser()

        when:
        notifyAnalyser(analyser, source)
        Throwable transformedFailure = analyser.transform(failure)

        then:
        LocationAwareException gse = (LocationAwareException) transformedFailure
        gse.sourceDisplayName == source.displayName
        gse.lineNumber == 7
    }

    def 'does not add location when location cannot be determined'() {
        given:
        Throwable failure = new ContextualException()

        when:
        Throwable transformedFailure = analyser().transform(failure)

        then:
        LocationAwareException gse = (LocationAwareException) transformedFailure
        gse.sourceDisplayName ==  null
        gse.lineNumber == null
    }

    def 'wraps contextual multi cause exception with location aware exception'() {
        given:
        Throwable cause1 = new ContextualException()
        Throwable cause2 = new ContextualException()
        Throwable failure = new ContextualMultiCauseException(cause1, cause2)

        when:
        Throwable transformedFailure = analyser().transform(failure)

        then:
        LocationAwareException gse = (LocationAwareException) transformedFailure
        gse.cause.is(failure)
        gse.reportableCauses == [cause1, cause2]
    }

    def 'uses original exception when it is already location aware'() {
        given:
        Throwable failure = locationAwareException(null)
        DefaultExceptionAnalyser analyser = analyser()

        when:
        notifyAnalyser(analyser, source)

        then:
        analyser.transform(failure).is(failure)
    }

    def 'uses deepest ScriptException exception'() {
        given:
        Throwable cause = new GradleScriptException("broken", new RuntimeException())
        Throwable failure = new GradleScriptException("broken", new RuntimeException(cause))

        when:
        Throwable transformedFailure = analyser().transform(failure)

        then:
        transformedFailure.cause.is(cause)
    }

    def 'uses deepest location aware exception'() {
        given:
        Throwable cause = locationAwareException(null)
        Throwable failure = locationAwareException(new RuntimeException(cause))

        when:
        Throwable transformedFailure = analyser().transform(failure)

        then:
        transformedFailure.is(cause)
    }

    def 'prefers script exception over contextual exception'() {
        given:
        Throwable cause = new GradleScriptException("broken", new ContextualException())
        Throwable failure = new TaskExecutionException(null, cause)

        when:
        Throwable transformedFailure = analyser().transform(failure)

        then:
        LocationAwareException gse = (LocationAwareException) transformedFailure
        gse.cause.is(cause)
    }

    def 'prefers location aware exception over script exception'() {
        given:
        Throwable cause = locationAwareException(new GradleScriptException("broken", new RuntimeException()))
        Throwable failure = new TaskExecutionException(null, cause)

        expect:
        analyser().transform(failure).is(cause)
    }

    def 'wraps arbitrary failure with location information'() {
        given:
        Throwable failure = new RuntimeException()
        failure.setStackTrace([element, otherElement, callerElement] as StackTraceElement[])
        DefaultExceptionAnalyser analyser = analyser()
        notifyAnalyser(analyser, source)

        when:
        Throwable transformedFailure = analyser.transform(failure)

        then:
        LocationAwareException gse = (LocationAwareException) transformedFailure
        gse.sourceDisplayName == source.displayName
        gse.lineNumber == 7
        gse.cause.is(failure)
    }

    private Throwable locationAwareException(final Throwable cause) {
        final Throwable failure = Mock(TestException.class)
        failure.getCause() >> cause
        failure.getStackTrace() >> ([element] as StackTraceElement[])
        return failure
    }

    private void notifyAnalyser(DefaultExceptionAnalyser analyser, final ScriptSource source) {
        analyser.scriptClassLoaded(source, Script.class)
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
