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
package org.gradle.execution;

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import static org.gradle.util.WrapUtil.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DryRunBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final BuildExecuter delegate = context.mock(BuildExecuter.class);
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final TaskGraphExecuter taskExecuter = context.mock(TaskGraphExecuter.class);
    private final DryRunBuildExecuter executer = new DryRunBuildExecuter(delegate);

    @Test
    public void disablesAllSelectedTasksBeforeExecution() {
        final Task task1 = context.mock(Task.class, "task1");
        final Task task2 = context.mock(Task.class, "task2");

        context.checking(new Expectations() {{
            allowing(gradle).getTaskGraph();
            will(returnValue(taskExecuter));

            one(delegate).select(gradle);
        }});

        executer.select(gradle);

        context.checking(new Expectations() {{
            one(taskExecuter).getAllTasks();
            will(returnValue(toList(task1, task2)));

            one(task1).setEnabled(false);
            one(task2).setEnabled(false);

            one(delegate).execute();
        }});

        executer.execute();
    }
}
