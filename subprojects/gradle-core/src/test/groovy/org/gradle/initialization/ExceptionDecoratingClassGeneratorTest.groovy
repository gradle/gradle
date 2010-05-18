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
package org.gradle.initialization


import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.Test
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.api.GradleException
import org.gradle.api.GradleScriptException
import org.gradle.api.ScriptCompilationException
import org.gradle.api.internal.Contextual
import org.gradle.api.LocationAwareException

public class ExceptionDecoratingClassGeneratorTest {
    private final ScriptSource script = [getDisplayName: {"description"}] as ScriptSource
    private final ExceptionDecoratingClassGenerator generator = new ExceptionDecoratingClassGenerator()

    @Test
    public void mixesLocationAwareIntoException() {
        RuntimeException cause = new RuntimeException()
        GradleException target = new GradleException("message", cause)

        GradleException exception = generator.newInstance(GradleException.class, target, script, 12)
        assertThat(exception.getClass().getPackage(), equalTo(GradleException.class.getPackage()))
        assertThat(exception.getClass().simpleName, equalTo('LocationAwareGradleException'))
        assertThat(exception, instanceOf(LocationAwareException))
        assertThat(exception.originalMessage, equalTo("message"))
        assertThat(exception.originalException, sameInstance(target))
        assertThat(exception.cause, sameInstance(cause))
        assertThat(exception.scriptSource, sameInstance(script))
        assertThat(exception.lineNumber, equalTo(12))
        assertThat(exception.stackTrace, equalTo(target.stackTrace))
    }

    @Test
    public void cachesGeneratedType() {
        assertSame(generator.generate(GradleException.class), generator.generate(GradleException.class))
    }

    @Test
    public void messageIncludesSourceFileAndLineNumber() {
        GradleException target = new GradleException("<message>")
        GradleException exception = generator.newInstance(GradleException.class, target, script, 91)
        assertThat(exception.location, equalTo("Description line: 91"));
        assertThat(exception.message, equalTo(String.format("Description line: 91%n<message>")));
    }

    @Test
    public void handlesExceptionWithNoLineNumber() {
        GradleException target = new GradleException("<message>")
        GradleException exception = generator.newInstance(GradleException.class, target, script, null)
        assertThat(exception.location, equalTo("Description"));
        assertThat(exception.message, equalTo(String.format("Description%n<message>")));
    }

    @Test
    public void handlesExceptionWithNoLocation() {
        GradleException target = new GradleException("<message>")
        GradleException exception = generator.newInstance(GradleException.class, target, null, null)
        assertThat(exception.location, nullValue());
        assertThat(exception.message, equalTo("<message>"));
    }

    @Test
    public void handlesExceptionWithNoMessage() {
        GradleException target = new GradleException()
        GradleException exception = generator.newInstance(GradleException.class, target, script, 91)
        assertThat(exception.location, equalTo("Description line: 91"));
        assertThat(exception.message, equalTo(String.format("Description line: 91")));
    }

    @Test
    public void usesAllCauseExceptionsWhichAreContextualAsReportableCauses() {
        RuntimeException actualCause = new RuntimeException(new Throwable());
        ContextualException contextualCause = new ContextualException(actualCause);
        ContextualException outerContextualCause = new ContextualException(contextualCause);
        GradleException target = new GradleException('fail', outerContextualCause)
        GradleException exception = generator.newInstance(GradleException.class, target, script, 91)
        assertThat(exception.reportableCauses, equalTo([outerContextualCause, contextualCause, actualCause]));
    }

    @Test
    public void usesDirectCauseAsReportableCauseWhenNoContextualCausesPresent() {
        RuntimeException actualCause = new RuntimeException(new Throwable());
        GradleException target = new GradleException('fail', actualCause)
        GradleException exception = generator.newInstance(GradleException.class, target, script, 91)
        assertThat(exception.reportableCauses, equalTo([actualCause]));
    }

    @Test
    public void handlesTaskWithCopyConstructor() {
        ScriptCompilationException target = new ScriptCompilationException("message", new RuntimeException(), script, 12)

        GradleScriptException exception = generator.newInstance(ScriptCompilationException.class, target, null, null)
        assertThat(exception.originalException, sameInstance(target))
    }
}

@Contextual
class ContextualException extends RuntimeException {
    private ContextualException() {
    }

    private ContextualException(Throwable throwable) {
        super(throwable);
    }
}

