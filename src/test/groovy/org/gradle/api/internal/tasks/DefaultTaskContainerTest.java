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

import org.gradle.api.*;
import org.gradle.api.internal.project.ITaskFactory;
import org.gradle.util.GUtil;
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

import java.util.Map;

@RunWith(JMock.class)
public class DefaultTaskContainerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ITaskFactory taskFactory = context.mock(ITaskFactory.class);
    private final Project project = context.mock(Project.class);
    private final DefaultTaskContainer container = new DefaultTaskContainer(project, taskFactory);

    @Test
    public void addsTaskWithMapAndClosure() {
        final Map<String, ?> options = GUtil.map("option", "value");
        final TaskAction action = context.mock(TaskAction.class);
        final Task task = context.mock(Task.class);

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, container.getAsMap(), options, "task");
            will(returnValue(task));
            one(task).doFirst(action);
        }});
        assertThat(container.add(options, "task", action), sameInstance(task));
        assertThat(container.getByName("task"), sameInstance(task));
    }
    
    @Test
    public void addsTaskWithName() {
        final Map<String, ?> options = GUtil.map();
        final Task task = context.mock(Task.class);

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, container.getAsMap(), options, "task");
            will(returnValue(task));
        }});
        assertThat(container.add("task"), sameInstance(task));
        assertThat(container.getByName("task"), sameInstance(task));
    }

    @Test
    public void addsTaskWithNameAndType() {
        final Map<String, ?> options = GUtil.map(Task.TASK_TYPE, Task.class);
        final Task task = context.mock(Task.class);

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, container.getAsMap(), options, "task");
            will(returnValue(task));
        }});
        assertThat(container.add("task", Task.class), sameInstance(task));
        assertThat(container.getByName("task"), sameInstance(task));
    }

    @Test
    public void replacesTaskWithName() {
        final Map<String, ?> options = GUtil.map(Task.TASK_OVERWRITE, true);
        final Task task = context.mock(Task.class);

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, container.getAsMap(), options, "task");
            will(returnValue(task));
        }});
        assertThat(container.replace("task"), sameInstance(task));
        assertThat(container.getByName("task"), sameInstance(task));
    }

    @Test
    public void replacesTaskWithNameAndType() {
        final Map<String, ?> options = GUtil.map(Task.TASK_TYPE, Task.class, Task.TASK_OVERWRITE, true);
        final Task task = context.mock(Task.class);

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, container.getAsMap(), options, "task");
            will(returnValue(task));
        }});
        assertThat(container.replace("task", Task.class), sameInstance(task));
        assertThat(container.getByName("task"), sameInstance(task));
    }

    @Test
    public void getByNameFailsForUnknownTask() {
        try {
            container.getByName("unknown");
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
        container.addObject("task", task);
    }

    @Test
    public void callsActionWhenTaskOfRequestedTypeAdded() {
        final Action<TestTask> action = context.mock(Action.class);
        final TestTask task = new TestTask(HelperUtil.createRootProject(), "task");

        context.checking(new Expectations() {{
            one(action).execute(task);
        }});

        container.whenTaskAdded(TestTask.class, action);
        container.addObject("task", task);
    }

    @Test
    public void doesNotCallActionWhenTaskOfNonRequestedTypeAdded() {
        final Action<TestTask> action = context.mock(Action.class);
        final Task task = context.mock(Task.class);

        container.whenTaskAdded(TestTask.class, action);
        container.addObject("task", task);
    }

    @Test
    public void callsClosureWhenTaskAdded() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Task task = context.mock(Task.class);
        context.checking(new Expectations() {{
            one(closure).call(task);
        }});

        container.whenTaskAdded(HelperUtil.toClosure(closure));
        container.addObject("task", task);
    }
}
