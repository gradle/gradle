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
package org.gradle.api.internal.changedetection;

import org.gradle.StartParameter;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.specs.Spec;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class ShortCircuitTaskArtifactStateRepositoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final StartParameter startParameter = new StartParameter();
    private final TaskArtifactStateRepository delegate = context.mock(TaskArtifactStateRepository.class);
    private final TaskInternal task = context.mock(TaskInternal.class);
    private final TaskArtifactState taskArtifactState = context.mock(TaskArtifactState.class);
    private final TaskOutputsInternal taskOutputsInternal = context.mock(TaskOutputsInternal.class);
    private final Spec<Task> upToDateSpec = context.mock(Spec.class);
    private final ShortCircuitTaskArtifactStateRepository repository = new ShortCircuitTaskArtifactStateRepository(startParameter, delegate);

    @Before
    public void setup() {
        context.checking(new Expectations() {{
            allowing(task).getOutputs();
            will(returnValue(taskOutputsInternal));
            allowing(taskOutputsInternal).getUpToDateSpec();
            will(returnValue(upToDateSpec));
        }});
    }

    @Test
    public void delegatesToBackingRepositoryToCreateStateObject() {
        expectTaskStateCreated();

        TaskArtifactState state = repository.getStateFor(task);
        assertNotNull(state);

        final TaskExecutionHistory executionHistory = context.mock(TaskExecutionHistory.class);

        context.checking(new Expectations() {{
            one(taskArtifactState).getExecutionHistory();
            will(returnValue(executionHistory));
            one(taskArtifactState).update();
        }});

        assertThat(state.getExecutionHistory(), sameInstance(executionHistory));
        state.update();
    }

    @Test
    public void taskArtifactsAreOutOfDateWhenStartParameterOverrideIsSet() {
        expectTaskStateCreated();

        TaskArtifactState state = repository.getStateFor(task);

        startParameter.setNoOpt(true);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void taskArtifactsAreOutOfDateWhenUpToDateSpecIsFalse() {
        expectTaskStateCreated();

        TaskArtifactState state = repository.getStateFor(task);

        context.checking(new Expectations() {{
            one(upToDateSpec).isSatisfiedBy(task);
            will(returnValue(false));
        }});

        assertFalse(state.isUpToDate());
    }

    @Test
    public void determinesWhetherTaskArtifactsAreUpToDateUsingBackingRepository() {
        expectTaskStateCreated();

        TaskArtifactState state = repository.getStateFor(task);

        context.checking(new Expectations() {{
            one(upToDateSpec).isSatisfiedBy(task);
            will(returnValue(true));
            one(taskArtifactState).isUpToDate();
            will(returnValue(true));
        }});

        assertTrue(state.isUpToDate());
    }

    private void expectTaskStateCreated() {
        context.checking(new Expectations() {{
            one(delegate).getStateFor(task);
            will(returnValue(taskArtifactState));
        }});
    }
}
