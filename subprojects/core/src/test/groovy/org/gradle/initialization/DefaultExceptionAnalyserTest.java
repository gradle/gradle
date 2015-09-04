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
package org.gradle.initialization;

import org.gradle.api.GradleScriptException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.groovy.scripts.Script;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.event.ListenerManager;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.toArray;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class DefaultExceptionAnalyserTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ListenerManager listenerManager = context.mock(ListenerManager.class);
    private final StackTraceElement element = new StackTraceElement("class", "method", "filename", 7);
    private final StackTraceElement callerElement = new StackTraceElement("class", "method", "filename", 11);
    private final StackTraceElement otherElement = new StackTraceElement("class", "method", "otherfile", 11);
    private final StackTraceElement elementWithNoSourceFile = new StackTraceElement("class", "method", null, 11);
    private final StackTraceElement elementWithNoLineNumber = new StackTraceElement("class", "method", "filename", -1);
    private final ScriptSource source = context.mock(ScriptSource.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(source).getFileName();
            will(returnValue("filename"));
            allowing(source).getDisplayName();
            will(returnValue("build file filename"));
        }});
    }

    @Test
    public void wrapsOriginalExceptionWhenItIsNotAContextualException() {
        Throwable failure = new RuntimeException();

        DefaultExceptionAnalyser analyser = analyser();
        Throwable transformed = analyser.transform(failure);
        assertThat(transformed, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformed;
        assertThat(gse.getCause(), sameInstance(failure));
        assertThat(gse.getReportableCauses(), isEmpty());
    }

    @Test
    public void wrapsContextualExceptionWithLocationAwareException() {
        Throwable failure = new ContextualException();

        DefaultExceptionAnalyser analyser = analyser();

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getCause(), sameInstance(failure));
        assertThat(gse.getReportableCauses(), isEmpty());
    }

    @Test
    public void wrapsHighestContextualExceptionWithLocationAwareException() {
        Throwable cause = new ContextualException();
        Throwable failure = new ContextualException(cause);

        DefaultExceptionAnalyser analyser = analyser();

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getCause(), sameInstance(failure));
        assertThat(gse.getReportableCauses(), equalTo(toList(cause)));
    }

    @Test
    public void addsLocationInfoFromDeepestStackFrameWithMatchingSourceFileAndLineInformation() {
        Throwable failure = new ContextualException();
        failure.setStackTrace(toArray(elementWithNoSourceFile, elementWithNoLineNumber, otherElement, element, callerElement));

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getSourceDisplayName(), equalTo(source.getDisplayName()));
        assertThat(gse.getLineNumber(), equalTo(7));
    }

    @Test
    public void addsLocationInfoFromDeepestCause() {
        RuntimeException cause = new RuntimeException();
        ContextualException failure = new ContextualException(new RuntimeException(cause));
        failure.setStackTrace(toArray(otherElement, callerElement));
        cause.setStackTrace(toArray(element, otherElement, callerElement));

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getSourceDisplayName(), equalTo(source.getDisplayName()));
        assertThat(gse.getLineNumber(), equalTo(7));
    }

    @Test
    public void doesNotAddLocationWhenLocationCannotBeDetermined() {
        Throwable failure = new ContextualException();
        Throwable transformedFailure = analyser().transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getSourceDisplayName(), nullValue());
        assertThat(gse.getLineNumber(), nullValue());
    }

    @Test
    public void wrapsContextualMultiCauseExceptionWithLocationAwareException() {
        Throwable cause1 = new ContextualException();
        Throwable cause2 = new ContextualException();
        Throwable failure = new ContextualMultiCauseException(cause1, cause2);

        Throwable transformedFailure = analyser().transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getCause(), sameInstance(failure));
        assertThat(gse.getReportableCauses(), equalTo(toList(cause1, cause2)));
    }

    @Test
    public void usesOriginalExceptionWhenItIsAlreadyLocationAware() {
        Throwable failure = locationAwareException(null);

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);
        
        assertThat(analyser.transform(failure), sameInstance(failure));
    }

    @Test
    public void usesDeepestScriptExceptionException() {
        Throwable cause = new GradleScriptException("broken", new RuntimeException());
        Throwable failure = new GradleScriptException("broken", new RuntimeException(cause));

        Throwable transformedFailure = analyser().transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getCause(), sameInstance(cause));
    }

    @Test
    public void usesDeepestLocationAwareException() {
        Throwable cause = locationAwareException(null);
        Throwable failure = locationAwareException(new RuntimeException(cause));

        DefaultExceptionAnalyser analyser = analyser();

        assertThat(analyser.transform(failure), sameInstance(cause));
    }

    @Test
    public void prefersScriptExceptionOverContextualException() {
        Throwable cause = new GradleScriptException("broken", new ContextualException());
        Throwable failure = new TaskExecutionException(null, cause);

        Throwable transformedFailure = analyser().transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getCause(), sameInstance(cause));
    }

    @Test
    public void prefersLocationAwareExceptionOverScriptException() {
        Throwable cause = locationAwareException(new GradleScriptException("broken", new RuntimeException()));
        Throwable failure = new TaskExecutionException(null, cause);

        DefaultExceptionAnalyser analyser = analyser();

        assertThat(analyser.transform(failure), sameInstance(cause));
    }

    @Test
    public void wrapsArbitraryFailureWithLocationInformation() {
        Throwable failure = new RuntimeException();
        failure.setStackTrace(toArray(element, otherElement, callerElement));

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getSourceDisplayName(), equalTo(source.getDisplayName()));
        assertThat(gse.getLineNumber(), equalTo(7));
        assertThat(gse.getCause(), sameInstance(failure));
    }

    private Throwable locationAwareException(final Throwable cause) {
        final Throwable failure = context.mock(TestException.class);
        context.checking(new Expectations() {{
            allowing(failure).getCause();
            will(returnValue(cause));
            allowing(failure).getStackTrace();
            will(returnValue(toArray(element)));
        }});
        return failure;
    }

    private void notifyAnalyser(DefaultExceptionAnalyser analyser, final ScriptSource source) {
        analyser.scriptClassLoaded(source, Script.class);
    }

    private DefaultExceptionAnalyser analyser() {
        context.checking(new Expectations() {{
            one(listenerManager).addListener(with(notNullValue(DefaultExceptionAnalyser.class)));
        }});
        return new DefaultExceptionAnalyser(listenerManager);
    }

    @Contextual
    public static class ContextualException extends RuntimeException {
        public ContextualException() {
            super("failed");
        }

        public ContextualException(Throwable throwable) {
            super(throwable);
        }
    }

    @Contextual
    public static class ContextualMultiCauseException extends RuntimeException implements MultiCauseException {
        private List<Throwable> causes;

        public ContextualMultiCauseException(Throwable... throwables) {
            this.causes = Arrays.asList(throwables);
        }

        public List<? extends Throwable> getCauses() {
            return causes;
        }
    }

    @Contextual
    public abstract static class TestException extends LocationAwareException {
        protected TestException(Throwable cause, ScriptSource source, Integer lineNumber) {
            super(cause, source, lineNumber);
        }
    }
}
