/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api;

import org.gradle.api.internal.Contextual;
import org.gradle.groovy.scripts.ScriptSource;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class GradleScriptExceptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ScriptSource source = context.mock(ScriptSource.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(source).getDisplayName();
            will(returnValue("<description>"));
        }});
    }

    @Test
    public void messageIncludesSourceAndLineNumber() {
        GradleScriptException exception = new GradleScriptException("<message>", new RuntimeException(), source, 91);
        assertThat(exception.getLocation(), equalTo("<description> line: 91"));
        assertThat(exception.getMessage(), equalTo(String.format("<description> line: 91%n<message>")));
    }

    @Test
    public void messageIndicatesWhenNoLineNumbersFound() {
        RuntimeException cause = new RuntimeException("<cause>");
        GradleScriptException exception = new GradleScriptException("<message>", cause, source);
        assertThat(exception.getLocation(), equalTo("<description>"));
        assertThat(exception.getMessage(), equalTo(String.format("<description>%n<message>")));
    }

    @Test
    public void reportableExceptionIsSelf() {
        RuntimeException cause = new RuntimeException("<cause>");
        GradleScriptException exception = new GradleScriptException("<message>", cause, source);
        assertThat(exception.getReportableException(), sameInstance(exception));
    }

    @Test
    public void usesAllCauseExceptionsWhichAreContextualAsReportableCauses() {
        RuntimeException actualCause = new RuntimeException(new Throwable());
        ContextualException contextualCause = new ContextualException(actualCause);
        GradleScriptException exception = new GradleScriptException("<message", contextualCause, source);
        assertThat(exception.getReportableCauses(), equalTo(toList((Throwable) contextualCause, actualCause)));

        ContextualException outerContextualCause = new ContextualException(contextualCause);

        exception = new GradleScriptException("<message", outerContextualCause, source);
        assertThat(exception.getReportableCauses(), equalTo(toList((Throwable) outerContextualCause, contextualCause,
                actualCause)));
    }

    @Test
    public void usesDirectCauseAsReportableCauseWhenNoContextualCausesPresent() {
        RuntimeException actualCause = new RuntimeException(new Throwable());
        GradleScriptException exception = new GradleScriptException("<message", actualCause, source);
        assertThat(exception.getReportableCauses(), equalTo(toList((Throwable) actualCause)));
    }

    @Contextual
    private class ContextualException extends RuntimeException {
        private ContextualException() {
        }

        private ContextualException(Throwable throwable) {
            super(throwable);
        }
    }
}
