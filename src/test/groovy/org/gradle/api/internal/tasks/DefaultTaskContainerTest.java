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
import org.gradle.api.UnknownTaskException;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestClosure;
import org.gradle.util.TestTask;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultTaskContainerTest {
    private final DefaultTaskContainer container = new DefaultTaskContainer();
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void getByNameFailsForUnknownTask() {
        try {
            container.get("unknown");
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task with name 'unknown' not found."));
        }
    }

    @Test
    public void callsActionWhenTaskAdded() {
        final Action<Task> action = context.mock(Action.class);
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            one(action).execute(task);
        }});

        container.whenTaskAdded(action);
        container.add("task", task);
    }

    @Test
    public void callsActionWhenTaskOfRequestedTypeAdded() {
        final Action<TestTask> action = context.mock(Action.class);
        final TestTask task = new TestTask(HelperUtil.createRootProject(), "task");

        context.checking(new Expectations() {{
            one(action).execute(task);
        }});

        container.whenTaskAdded(TestTask.class, action);
        container.add("task", task);
    }

    @Test
    public void doesNotCallActionWhenTaskOfNonRequestedTypeAdded() {
        final Action<TestTask> action = context.mock(Action.class);
        final Task task = context.mock(Task.class);

        container.whenTaskAdded(TestTask.class, action);
        container.add("task", task);
    }

    @Test
    public void callsClosureWhenTaskAdded() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Task task = context.mock(Task.class);
        context.checking(new Expectations() {{
            one(closure).call(task);
        }});

        container.whenTaskAdded(HelperUtil.toClosure(closure));
        container.add("task", task);
    }
}
