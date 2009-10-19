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

import org.gradle.api.GradleScriptException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.groovy.scripts.ScriptSource;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class SkipTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TaskInternal task = context.mock(TaskInternal.class, "<task>");
    private final Spec<Task> spec = context.mock(Spec.class);
    private final TaskState state = new TaskState();
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final TaskExecuter delegate = context.mock(TaskExecuter.class);
    private final SkipTaskExecuter executer = new SkipTaskExecuter(delegate);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            ProjectInternal project = context.mock(ProjectInternal.class);

            allowing(task).getProject();
            will(returnValue(project));

            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));

            allowing(task).getOnlyIf();
            will(returnValue(spec));

            ignoring(scriptSource);
        }});
    }

    @Test
    public void executesTask() {
        final TaskExecutionResult result = context.mock(TaskExecutionResult.class);

        context.checking(new Expectations() {{
            allowing(task).getEnabled();
            will(returnValue(true));

            allowing(spec).isSatisfiedBy(task);
            will(returnValue(true));

            one(delegate).execute(task, state);
            will(returnValue(result));
        }});

        assertThat(executer.execute(task, state), sameInstance(result));

        assertTrue(state.isExecuted());
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
        assertFalse(state.isDidWork());
    }
}