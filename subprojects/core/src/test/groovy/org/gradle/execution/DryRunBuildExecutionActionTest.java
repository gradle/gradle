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

import org.gradle.StartParameter;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.WrapUtil.toList;

@RunWith(JMock.class)
public class DryRunBuildExecutionActionTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final BuildExecutionContext executionContext = context.mock(BuildExecutionContext.class);
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final TaskGraphExecuter taskExecuter = context.mock(TaskGraphExecuter.class);
    private final StartParameter startParameter = context.mock(StartParameter.class);
    private final DryRunBuildExecutionAction action = new DryRunBuildExecutionAction();

    @Before
    public void setup() {
        context.checking(new Expectations(){{
            allowing(gradle).getStartParameter();
            will(returnValue(startParameter));
            allowing(executionContext).getGradle();
            will(returnValue(gradle));
            allowing(gradle).getTaskGraph();
            will(returnValue(taskExecuter));
        }});
    }
    
    @Test
    public void disablesAllSelectedTasksBeforeProceedingWhenDryRunIsEnabled() {
        final Task task1 = context.mock(Task.class, "task1");
        final Task task2 = context.mock(Task.class, "task2");

        context.checking(new Expectations() {{
            allowing(startParameter).isDryRun();
            will(returnValue(true));

            one(taskExecuter).getAllTasks();
            will(returnValue(toList(task1, task2)));

            one(task1).setEnabled(false);
            one(task2).setEnabled(false);

            one(executionContext).proceed();
        }});

        action.execute(executionContext);
    }

    @Test
    public void proceedsWhenDryRunIsNotSelected() {
        context.checking(new Expectations() {{
            allowing(startParameter).isDryRun();
            will(returnValue(false));

            one(executionContext).proceed();
        }});

        action.execute(executionContext);
    }
}
