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
package org.gradle.api.internal.project;

import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskState;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class ExecutionShortCircuitTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TaskExecuter delegate = context.mock(TaskExecuter.class);
    private final TaskInternal task = context.mock(TaskInternal.class);
    private final TaskState taskState = new TaskState();
    private final TaskExecutionResult result = context.mock(TaskExecutionResult.class);
    private final TaskArtifactStateRepository repository = context.mock(TaskArtifactStateRepository.class);
    private final TaskArtifactState taskArtifactState = context.mock(TaskArtifactState.class);
    private final ExecutionShortCircuitTaskExecuter executer = new ExecutionShortCircuitTaskExecuter(delegate, repository);

    @Test
    public void skipsTaskWhenOutputsAreUpToDate() {
        context.checking(new Expectations() {{
            one(repository).getStateFor(task);
            will(returnValue(taskArtifactState));
            one(taskArtifactState).isUpToDate();
            will(returnValue(true));
            allowing(task).getPath();
            will(returnValue(":task"));
        }});

        TaskExecutionResult result = executer.execute(task, taskState);
        assertThat(result, notNullValue());
        assertThat(result.getSkipMessage(), equalTo("UP-TO-DATE"));
        assertThat(result.getFailure(), nullValue());
        result.rethrowFailure();
    }
    
    @Test
    public void executesTaskWhenOutputsAreNotUpToDate() {
        context.checking(new Expectations() {{
            one(repository).getStateFor(task);
            will(returnValue(taskArtifactState));

            one(taskArtifactState).isUpToDate();
            will(returnValue(false));

            one(taskArtifactState).invalidate();

            one(delegate).execute(task, taskState);
            will(returnValue(result));

            allowing(result).getFailure();
            will(returnValue(null));

            one(taskArtifactState).update();
        }});

        assertThat(executer.execute(task, taskState), sameInstance(result));
    }

    @Test
    public void invalidatesStateWhenTaskFails() {
        context.checking(new Expectations() {{
            one(repository).getStateFor(task);
            will(returnValue(taskArtifactState));

            one(taskArtifactState).isUpToDate();
            will(returnValue(false));

            one(taskArtifactState).invalidate();

            one(delegate).execute(task, taskState);
            will(returnValue(result));

            allowing(result).getFailure();
            will(returnValue(new RuntimeException()));
        }});

        assertThat(executer.execute(task, taskState), sameInstance(result));
    }
}
