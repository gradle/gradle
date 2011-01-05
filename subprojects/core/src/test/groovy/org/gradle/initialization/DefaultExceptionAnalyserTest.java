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

import org.gradle.api.LocationAwareException;
import org.gradle.api.internal.Contextual;
import org.gradle.groovy.scripts.Script;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.listener.ListenerManager;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultExceptionAnalyserTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final ListenerManager listenerManager = context.mock(ListenerManager.class);
    private final StackTraceElement element = new StackTraceElement("class", "method", "filename", 7);
    private final StackTraceElement callerElement = new StackTraceElement("class", "method", "filename", 11);
    private final StackTraceElement otherElement = new StackTraceElement("class", "method", "otherfile", 11);
    private final ScriptSource source = context.mock(ScriptSource.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(source).getFileName();
            will(returnValue("filename"));
        }});
    }

    @Test
    public void usesOriginalExceptionWhenItIsNotAContextualException() {
        Throwable failure = new RuntimeException();

        DefaultExceptionAnalyser analyser = analyser();
        assertThat(analyser.transform(failure), sameInstance(failure));
    }

    @Test
    public void wrapsContextualExceptionWithLocationInfoFromDeepestStackFrame() {
        ContextualException failure = new ContextualException();
        failure.setStackTrace(WrapUtil.toArray(element, otherElement, callerElement));

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getScriptSource(), sameInstance(source));
        assertThat(gse.getLineNumber(), equalTo(7));
    }

    @Test
    public void wrapsContextualExceptionWithLocationInfoFromDeepestCause() {
        RuntimeException cause = new RuntimeException();
        ContextualException failure = new ContextualException(new RuntimeException(cause));
        failure.setStackTrace(WrapUtil.toArray(otherElement, callerElement));
        cause.setStackTrace(WrapUtil.toArray(element, otherElement, callerElement));

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getScriptSource(), sameInstance(source));
        assertThat(gse.getLineNumber(), equalTo(7));
    }

    @Test
    public void wrapsDeepestContextualExceptionWithLocationInfo() {
        ContextualException cause = new ContextualException();
        ContextualException failure = new ContextualException(new RuntimeException(cause));
        failure.setStackTrace(WrapUtil.toArray(otherElement, callerElement));
        cause.setStackTrace(WrapUtil.toArray(element, otherElement, callerElement));

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);

        Throwable transformedFailure = analyser.transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getScriptSource(), sameInstance(source));
        assertThat(gse.getLineNumber(), equalTo(7));
    }

    @Test
    public void wrapsOriginalExceptionWhenLocationCannotBeDetermined() {
        Throwable failure = new ContextualException();
        Throwable transformedFailure = analyser().transform(failure);
        assertThat(transformedFailure, instanceOf(LocationAwareException.class));

        LocationAwareException gse = (LocationAwareException) transformedFailure;
        assertThat(gse.getScriptSource(), nullValue());
        assertThat(gse.getLineNumber(), nullValue());
    }

    @Test
    public void usesOriginalExceptionWhenItIsAlreadyLocationAware() {
        final Throwable failure = context.mock(TestException.class);
        context.checking(new Expectations() {{
            allowing(failure).getCause();
            will(returnValue(null));
            allowing(failure).getStackTrace();
            will(returnValue(WrapUtil.toArray(element)));
        }});

        DefaultExceptionAnalyser analyser = analyser();
        notifyAnalyser(analyser, source);
        
        assertThat(analyser.transform(failure), sameInstance(failure));
    }

    private void notifyAnalyser(DefaultExceptionAnalyser analyser, final ScriptSource source) {
        final Script script = context.mock(Script.class);
        context.checking(new Expectations() {{
            allowing(script).getScriptSource();
            will(returnValue(source));
        }});
        analyser.beforeScript(script);
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
    public abstract static class TestException extends RuntimeException implements LocationAwareException {

    }
}
