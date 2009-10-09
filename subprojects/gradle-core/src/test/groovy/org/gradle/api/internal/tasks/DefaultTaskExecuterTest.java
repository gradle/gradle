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
package org.gradle.api.internal.tasks;

import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.Task;
import org.gradle.api.Action;
import org.gradle.api.GradleScriptException;
import org.gradle.api.logging.StandardOutputCapture;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.StopActionException;
import static org.gradle.util.WrapUtil.*;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.StartParameter;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TaskInternal task = context.mock(TaskInternal.class, "<task>");
    private final Action<Task> action1 = context.mock(Action.class, "action1");
    private final Action<Task> action2 = context.mock(Action.class, "action2");
    private final Spec<Task> spec = context.mock(Spec.class);
    private final TaskState state = new TaskState();
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final StandardOutputCapture standardOutputCapture = context.mock(StandardOutputCapture.class);
    private final Sequence sequence = context.sequence("seq");
    private final DefaultTaskExecuter executer = new DefaultTaskExecuter(new StartParameter());

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            ProjectInternal project = context.mock(ProjectInternal.class);

            allowing(task).getPath();
            will(returnValue(":task"));

            allowing(task).getProject();
            will(returnValue(project));

            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));

            allowing(task).getOnlyIf();
            will(returnValue(spec));

            allowing(task).getStandardOutputCapture();
            will(returnValue(standardOutputCapture));

            ignoring(scriptSource);
        }});
    }

    @Test
    public void doesNothingWhenTaskHasNoActions() {
        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList()));
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertFalse(state.isDidWork());
    }

    private void expectTaskEnabled() {
        context.checking(new Expectations(){{
            allowing(task).getEnabled();
            will(returnValue(true));

            allowing(spec).isSatisfiedBy(task);
            will(returnValue(true));
        }});
    }

    @Test
    public void executesEachActionInOrder() {
        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).execute(task);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action2).execute(task);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertTrue(state.isDidWork());
    }

    @Test
    public void stopsAtFirstActionWhichThrowsException() {
        final Throwable failure = new RuntimeException("failure");
        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(failure));
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), instanceOf(GradleScriptException.class));
        GradleScriptException exception = (GradleScriptException) result.getFailure();
        assertThat(exception.getOriginalMessage(), equalTo("Execution failed for <task>."));
        assertThat(exception.getCause(), sameInstance(failure));
        assertThat(exception.getScriptSource(), sameInstance(scriptSource));

        assertThat(result.getSkipMessage(), nullValue());
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertTrue(state.isDidWork());
    }

    @Test
    public void rethrowsWrappedException() {
        final Throwable failure = new RuntimeException("failure");
        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            ignoring(standardOutputCapture);

            one(action1).execute(task);
            will(throwException(failure));
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        try {
            result.rethrowFailure();
            fail();
        } catch (GradleScriptException e) {
            assertThat(e, sameInstance(result.getFailure()));
        }
    }

    @Test
    public void stopsAtFirstActionWhichThrowsStopExecutionException() {
        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(new StopExecutionException("stop")));
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertTrue(state.isDidWork());
    }

    @Test
    public void skipsActionWhichThrowsStopActionException() {
        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(new StopActionException("stop")));
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action2).execute(task);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertTrue(state.isDidWork());
    }

    @Test
    public void setsDidWorkFlagOnceFirstActionIsStarted() {
        final Action<Task> action = new Action<Task>() {
            public void execute(Task task) {
                assertTrue(state.isDidWork());
            }
        };

        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action)));

            ignoring(standardOutputCapture);
        }});

        executer.execute(task, state).rethrowFailure();
    }

    @Test
    public void setsExecutingFlagWhileTaskIsExecuting() {
        final Action<Task> action = new Action<Task>() {
            public void execute(Task task) {
                assertTrue(state.isExecuting());
            }
        };

        expectTaskEnabled();
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action)));

            ignoring(standardOutputCapture);
        }});

        executer.execute(task, state).rethrowFailure();
    }

    @Test
    public void skipsDisabledTask() {
        context.checking(new Expectations() {{
            allowing(task).getEnabled();
            will(returnValue(false));
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), equalTo("SKIPPED"));
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertFalse(state.isDidWork());
    }

    @Test
    public void skipsTaskWhoseOnlyIfPredicateIsFalse() {
        context.checking(new Expectations() {{
            allowing(task).getEnabled();
            will(returnValue(true));
            one(spec).isSatisfiedBy(task);
            will(returnValue(false));
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), equalTo("SKIPPED as onlyIf is false"));
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertFalse(state.isDidWork());
    }

    @Test
    public void wrapsOnlyIfPredicateFailure() {
        final Throwable failure = new RuntimeException();
        context.checking(new Expectations() {{
            allowing(task).getEnabled();
            will(returnValue(true));
            one(spec).isSatisfiedBy(task);
            will(throwException(failure));
        }});

        TaskExecutionResult result = executer.execute(task, state);

        GradleScriptException exception = (GradleScriptException) result.getFailure();
        assertThat(exception.getOriginalMessage(), equalTo("Could not evaluate onlyIf predicate for <task>."));
        assertThat(exception.getCause(), sameInstance(failure));
        assertThat(exception.getScriptSource(), sameInstance(scriptSource));

        assertThat(result.getSkipMessage(), nullValue());
        assertTrue(state.isExecuted());
        assertFalse(state.isExecuting());
        assertFalse(state.isDidWork());
    }
}
