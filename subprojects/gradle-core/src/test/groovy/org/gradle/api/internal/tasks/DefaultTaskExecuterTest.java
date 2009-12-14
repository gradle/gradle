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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.StandardOutputCapture;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.groovy.scripts.ScriptSource;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TaskInternal task = context.mock(TaskInternal.class, "<task>");
    private final Action<Task> action1 = context.mock(Action.class, "action1");
    private final Action<Task> action2 = context.mock(Action.class, "action2");
    private final TaskState state = new TaskState();
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final StandardOutputCapture standardOutputCapture = context.mock(StandardOutputCapture.class);
    private final Sequence sequence = context.sequence("seq");
    private final TaskActionListener listener = context.mock(TaskActionListener.class);
    private final DefaultTaskExecuter executer = new DefaultTaskExecuter(listener);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            ProjectInternal project = context.mock(ProjectInternal.class);

            allowing(task).getProject();
            will(returnValue(project));

            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));

            allowing(task).getStandardOutputCapture();
            will(returnValue(standardOutputCapture));

            ignoring(scriptSource);
        }});
    }

    @Test
    public void doesNothingWhenTaskHasNoActions() {
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList()));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
        assertFalse(state.isExecuting());
        assertFalse(state.isDidWork());
    }

    @Test
    public void executesEachActionInOrder() {
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

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

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
        assertFalse(state.isExecuting());
        assertTrue(state.isDidWork());
    }

    @Test
    public void stopsAtFirstActionWhichThrowsException() {
        final Throwable failure = new RuntimeException("failure");
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(failure));
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);
            
            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), instanceOf(TaskExecutionException.class));
        TaskExecutionException exception = (TaskExecutionException) result.getFailure();
        assertThat(exception.getTask(), equalTo((Task) task));
        assertThat(exception.getMessage(), equalTo("Execution failed for <task>."));
        assertThat(exception.getCause(), sameInstance(failure));

        assertThat(result.getSkipMessage(), nullValue());
        assertFalse(state.isExecuting());
        assertTrue(state.isDidWork());
    }

    @Test
    public void rethrowsWrappedException() {
        final Throwable failure = new RuntimeException("failure");
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            ignoring(standardOutputCapture);
            ignoring(listener);

            one(action1).execute(task);
            will(throwException(failure));
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        try {
            result.rethrowFailure();
            fail();
        } catch (TaskExecutionException e) {
            assertThat(e, sameInstance(result.getFailure()));
        }
    }

    @Test
    public void stopsAtFirstActionWhichThrowsStopExecutionException() {
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(new StopExecutionException("stop")));
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
        assertFalse(state.isExecuting());
        assertTrue(state.isDidWork());
    }

    @Test
    public void skipsActionWhichThrowsStopActionException() {
        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

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

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        TaskExecutionResult result = executer.execute(task, state);

        assertThat(result.getFailure(), nullValue());
        assertThat(result.getSkipMessage(), nullValue());
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

        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action)));

            ignoring(standardOutputCapture);
            ignoring(listener);
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

        context.checking(new Expectations() {{
            allowing(task).getActions();
            will(returnValue(toList(action)));

            ignoring(standardOutputCapture);
            ignoring(listener);
        }});

        executer.execute(task, state).rethrowFailure();
    }
}
