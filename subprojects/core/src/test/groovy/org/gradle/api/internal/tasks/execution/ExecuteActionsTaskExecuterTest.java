/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.execution;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.logging.StandardOutputCapture;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.action.CustomAction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.Collections.emptyList;
import static org.gradle.util.Matchers.*;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class ExecuteActionsTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final TaskInternal task = context.mock(TaskInternal.class, "<task>");
    private final ContextAwareTaskAction action1 = context.mock(ContextAwareTaskAction.class, "action1");
    private final ContextAwareTaskAction action2 = context.mock(ContextAwareTaskAction.class, "action2");
    private final TaskStateInternal state = context.mock(TaskStateInternal.class);
    private final TaskExecutionContext executionContext = context.mock(TaskExecutionContext.class);
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final StandardOutputCapture standardOutputCapture = context.mock(StandardOutputCapture.class);
    private final Sequence sequence = context.sequence("seq");
    private final TaskActionListener listener = context.mock(TaskActionListener.class);
    private final ExecuteActionsTaskExecuter executer = new ExecuteActionsTaskExecuter(listener);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
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
            allowing(task).getTaskActions();
            will(returnValue(emptyList()));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(state).setExecuting(true);
            inSequence(sequence);

            one(state).executed(null);
            inSequence(sequence);

            one(state).setExecuting(false);
            inSequence(sequence);

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void executesEachActionInOrder() {
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(state).setExecuting(true);
            inSequence(sequence);

            one(state).setDidWork(true);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).contextualise(executionContext);
            inSequence(sequence);

            one(action1).execute(task);
            inSequence(sequence);

            one(action1).contextualise(null);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(state).setDidWork(true);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action2).contextualise(executionContext);
            inSequence(sequence);

            one(action2).execute(task);
            inSequence(sequence);

            one(action2).contextualise(null);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(state).executed(null);
            inSequence(sequence);

            one(state).setExecuting(false);
            inSequence(sequence);

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void executeDoesOperateOnNewActionListInstance() {
        context.checking(new Expectations() {
            {
                allowing(task).getActions();
                will(returnValue(toList(action1)));

                allowing(task).getTaskActions();
                will(returnValue(toList(action1)));

                one(listener).beforeActions(task);
                inSequence(sequence);

                one(state).setExecuting(true);
                inSequence(sequence);

                one(state).setDidWork(true);
                inSequence(sequence);

                one(standardOutputCapture).start();
                inSequence(sequence);

                one(action1).contextualise(executionContext);
                inSequence(sequence);

                one(action1).execute(task);
                will(new CustomAction("Add action to actions list") {
                    public Object invoke(Invocation invocation) throws Throwable {
                        task.getActions().add(action2);
                        return null;
                    }
                });

                inSequence(sequence);

                one(action1).contextualise(null);
                inSequence(sequence);

                one(standardOutputCapture).stop();
                one(state).executed(null);
                inSequence(sequence);

                one(state).setExecuting(false);
                inSequence(sequence);

                one(listener).afterActions(task);
                inSequence(sequence);
            }
        });
        executer.execute(task, state, executionContext);
    }


    @Test
    public void stopsAtFirstActionWhichThrowsException() {
        final Throwable failure = new RuntimeException("failure");
        final Collector<Throwable> wrappedFailure = collector();
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(state).setExecuting(true);
            inSequence(sequence);

            one(state).setDidWork(true);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).contextualise(executionContext);
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(failure));
            inSequence(sequence);

            one(action1).contextualise(null);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(state).executed(with(notNullValue(Throwable.class)));
            will(collectTo(wrappedFailure));
            inSequence(sequence);

            one(state).setExecuting(false);
            inSequence(sequence);

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);

        assertThat(wrappedFailure.get(), instanceOf(TaskExecutionException.class));
        TaskExecutionException exception = (TaskExecutionException) wrappedFailure.get();
        assertThat(exception.getTask(), equalTo((Task) task));
        assertThat(exception.getMessage(), equalTo("Execution failed for <task>."));
        assertThat(exception.getCause(), sameInstance(failure));
    }

    @Test
    public void stopsAtFirstActionWhichThrowsStopExecutionException() {
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(state).setExecuting(true);
            inSequence(sequence);

            one(state).setDidWork(true);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).contextualise(executionContext);
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(new StopExecutionException("stop")));
            inSequence(sequence);

            one(action1).contextualise(null);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(state).executed(null);
            inSequence(sequence);

            one(state).setExecuting(false);
            inSequence(sequence);

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void skipsActionWhichThrowsStopActionException() {
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            one(listener).beforeActions(task);
            inSequence(sequence);

            one(state).setExecuting(true);
            inSequence(sequence);

            one(state).setDidWork(true);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action1).contextualise(executionContext);
            inSequence(sequence);

            one(action1).execute(task);
            will(throwException(new StopActionException("stop")));
            inSequence(sequence);

            one(action1).contextualise(null);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(state).setDidWork(true);
            inSequence(sequence);

            one(standardOutputCapture).start();
            inSequence(sequence);

            one(action2).contextualise(executionContext);
            inSequence(sequence);

            one(action2).execute(task);
            inSequence(sequence);

            one(action2).contextualise(null);
            inSequence(sequence);

            one(standardOutputCapture).stop();
            inSequence(sequence);

            one(state).executed(null);
            inSequence(sequence);

            one(state).setExecuting(false);
            inSequence(sequence);

            one(listener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }
}
