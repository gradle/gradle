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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.specs.Spec;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class SkipOnlyIfTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final TaskInternal task = context.mock(TaskInternal.class, "<task>");
    private final Spec<Task> spec = context.mock(Spec.class);
    private final TaskStateInternal state = context.mock(TaskStateInternal.class);
    private final TaskExecutionContext executionContext = context.mock(TaskExecutionContext.class);

    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final TaskExecuter delegate = context.mock(TaskExecuter.class);
    private final SkipOnlyIfTaskExecuter executer = new SkipOnlyIfTaskExecuter(delegate);

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
        context.checking(new Expectations() {{
            allowing(task).getEnabled();
            will(returnValue(true));

            allowing(spec).isSatisfiedBy(task);
            will(returnValue(true));

            one(delegate).execute(task, state, executionContext);
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void skipsTaskWhoseOnlyIfPredicateIsFalse() {
        context.checking(new Expectations() {{
            allowing(task).getEnabled();
            will(returnValue(true));
            one(spec).isSatisfiedBy(task);
            will(returnValue(false));
            one(state).skipped("SKIPPED");
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void wrapsOnlyIfPredicateFailure() {
        final Throwable failure = new RuntimeException();
        final Collector<Throwable> wrappedFailure = collector();
        context.checking(new Expectations() {{
            allowing(task).getEnabled();
            will(returnValue(true));
            one(spec).isSatisfiedBy(task);
            will(throwException(failure));
            one(state).executed(with(notNullValue(GradleException.class)));
            will(collectTo(wrappedFailure));
        }});

        executer.execute(task, state, executionContext);

        GradleException exception = (GradleException) wrappedFailure.get();
        assertThat(exception.getMessage(), equalTo("Could not evaluate onlyIf predicate for <task>."));
        assertThat(exception.getCause(), sameInstance(failure));
    }
}