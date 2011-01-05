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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class ExecutionShortCircuitTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final TaskExecuter delegate = context.mock(TaskExecuter.class);
    private final TaskOutputsInternal outputs = context.mock(TaskOutputsInternal.class);
    private final TaskInternal task = context.mock(TaskInternal.class);
    private final TaskStateInternal taskState = context.mock(TaskStateInternal.class);
    private final TaskArtifactStateRepository repository = context.mock(TaskArtifactStateRepository.class);
    private final TaskArtifactState taskArtifactState = context.mock(TaskArtifactState.class);
    private final ExecutionShortCircuitTaskExecuter executer = new ExecutionShortCircuitTaskExecuter(delegate, repository);

    @Before
    public void setup() {

        context.checking(new Expectations(){{
            allowing(task).getOutputs();
            will(returnValue(outputs));
        }});
    }
    @Test
    public void skipsTaskWhenOutputsAreUpToDate() {
        context.checking(new Expectations() {{
            one(repository).getStateFor(task);
            will(returnValue(taskArtifactState));

            one(taskArtifactState).isUpToDate();
            will(returnValue(true));

            one(taskState).upToDate();
        }});

        executer.execute(task, taskState);
    }
    
    @Test
    public void executesTaskWhenOutputsAreNotUpToDate() {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            one(repository).getStateFor(task);
            will(returnValue(taskArtifactState));
            inSequence(sequence);

            one(taskArtifactState).isUpToDate();
            will(returnValue(false));
            inSequence(sequence);

            one(outputs).setHistory(taskArtifactState);
            inSequence(sequence);

            one(delegate).execute(task, taskState);
            inSequence(sequence);

            allowing(taskState).getFailure();
            will(returnValue(null));

            one(taskArtifactState).update();
            inSequence(sequence);

            one(outputs).setHistory(null);
            inSequence(sequence);
        }});

        executer.execute(task, taskState);
    }

    @Test
    public void doesNotUpdateStateWhenTaskFails() {
        context.checking(new Expectations() {{
            one(repository).getStateFor(task);
            will(returnValue(taskArtifactState));

            one(taskArtifactState).isUpToDate();
            will(returnValue(false));

            one(outputs).setHistory(taskArtifactState);

            one(delegate).execute(task, taskState);

            allowing(taskState).getFailure();
            will(returnValue(new RuntimeException()));

            one(outputs).setHistory(null);
        }});

        executer.execute(task, taskState);
    }
}
