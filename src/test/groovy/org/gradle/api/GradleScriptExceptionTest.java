package org.gradle.api;

import org.gradle.groovy.scripts.ScriptSource;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class GradleScriptExceptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final StackTraceElement element = new StackTraceElement("class", "method", "filename", 7);
    private final ScriptSource source = context.mock(ScriptSource.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(source).getClassName();
            will(returnValue("filename"));
            allowing(source).getDescription();
            will(returnValue("<description>"));
        }});
    }

    @Test
    public void extractsLineNumbersFromStackTrace() {
        RuntimeException cause = new RuntimeException("<cause>");
        GradleScriptException exception = new GradleScriptException("<message>", cause, source);
        exception.setStackTrace(new StackTraceElement[]{element});
        assertThat(exception.getLocation(), equalTo("<description> line(s): 7"));
        assertThat(exception.getMessage(), equalTo(String.format("<description> line(s): 7%n<message>")));
    }

    @Test
    public void extractsLineNumbersFromStackTraceOfCause() {
        RuntimeException cause = new RuntimeException("<cause>");
        cause.setStackTrace(new StackTraceElement[]{element});
        GradleScriptException exception = new GradleScriptException("<message>", cause, source);
        assertThat(exception.getLocation(), equalTo("<description> line(s): 7"));
        assertThat(exception.getMessage(), equalTo(String.format("<description> line(s): 7%n<message>")));
    }

    @Test
    public void messageIndicatesWhenNoLineNumbersFound() {
        RuntimeException cause = new RuntimeException("<cause>");
        GradleScriptException exception = new GradleScriptException("<message>", cause, source);
        assertThat(exception.getLocation(), equalTo("<description>"));
        assertThat(exception.getMessage(), equalTo(String.format("<description>%n<message>")));
    }
}
