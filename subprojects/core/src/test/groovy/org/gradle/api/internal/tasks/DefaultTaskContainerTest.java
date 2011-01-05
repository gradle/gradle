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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.*;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
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
    private final ProjectInternal project = context.mock(ProjectInternal.class, "<project>");
    private int taskCount;
    private final DefaultTaskContainer container = new DefaultTaskContainer(project, context.mock(ClassGenerator.class), taskFactory);

    @Test
    public void addsTaskWithMap() {
        final Map<String, ?> options = GUtil.map("option", "value");
        final Task task = task("task");

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
        }});
        assertThat(container.add(options), sameInstance(task));
        assertThat(container.getByName("task"), sameInstance(task));
    }

    @Test
    public void addsTaskWithName() {
        final Map<String, ?> options = GUtil.map(Task.TASK_NAME, "task");
        final Task task = task("task");

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
        }});
        assertThat(container.add("task"), sameInstance(task));
    }

    @Test
    public void addsTaskWithNameAndType() {
        final Map<String, ?> options = GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, Task.class);
        final Task task = task("task");

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
        }});
        assertThat(container.add("task", Task.class), sameInstance(task));
    }

    @Test
    public void addsTaskWithNameAndConfigureClosure() {
        final Closure action = HelperUtil.toClosure("{ description = 'description' }");
        final Map<String, ?> options = GUtil.map(Task.TASK_NAME, "task");
        final Task task = task("task");

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
            one(task).configure(action);
            will(returnValue(task));
        }});
        assertThat(container.add("task", action), sameInstance(task));
    }

    @Test
    public void replacesTaskWithName() {
        final Map<String, ?> options = GUtil.map(Task.TASK_NAME, "task");
        final Task task = task("task");

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
        }});
        assertThat(container.replace("task"), sameInstance(task));
        assertThat(container.getByName("task"), sameInstance(task));
    }

    @Test
    public void replacesTaskWithNameAndType() {
        final Map<String, ?> options = GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, Task.class);
        final Task task = task("task");

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
        }});
        assertThat(container.replace("task", Task.class), sameInstance(task));
    }

    @Test
    public void doesNotFireRuleWhenAddingTask() {
        Rule rule = context.mock(Rule.class);
        final Map<String, ?> options = GUtil.map(Task.TASK_NAME, "task");
        final Task task = task("task");

        container.addRule(rule);

        context.checking(new Expectations(){{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
        }});

        container.add("task");
    }
    
    @Test
    public void cannotAddDuplicateTask() {
        final Task task = addTask("task");

        context.checking(new Expectations() {{
            one(taskFactory).createTask(project, GUtil.map(Task.TASK_NAME, "task"));
            will(returnValue(task("task")));
        }});

        try {
            container.add("task");
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot add [task2] as a task with that name already exists."));
        }

        assertThat(container.getByName("task"), sameInstance(task));
    }

    @Test
    public void canReplaceDuplicateTask() {
        addTask("task");

        final Task newTask = task("task");
        context.checking(new Expectations() {{
            one(taskFactory).createTask(project, GUtil.map(Task.TASK_NAME, "task"));
            will(returnValue(newTask));
        }});
        
        container.replace("task");
        assertThat(container.getByName("task"), sameInstance(newTask));
    }

    @Test
    public void getByNameFailsForUnknownTask() {
        try {
            container.getByName("unknown");
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task with name 'unknown' not found in <project>."));
        }
    }

    @Test
    public void canFindTaskByName() {
        Task task = addTask("task");

        assertThat(container.findByPath("task"), sameInstance(task));
    }

    @Test
    public void canFindTaskByRelativePath() {
        Task task = task("task");
        expectTaskLookupInOtherProject("sub", "task", task);

        assertThat(container.findByPath("sub:task"), sameInstance(task));
    }

    @Test
    public void canFindTaskByAbsolutePath() {
        Task task = task("task");
        expectTaskLookupInOtherProject(":", "task", task);

        assertThat(container.findByPath(":task"), sameInstance(task));
    }

    @Test
    public void findByPathReturnsNullForUnknownProject() {
        context.checking(new Expectations(){{
            allowing(project).findProject(":unknown");
            will(returnValue(null));
        }});

        assertThat(container.findByPath(":unknown:task"), nullValue());
    }

    @Test
    public void findByPathReturnsNullForUnknownTask() {
        expectTaskLookupInOtherProject(":other", "task", null);

        assertThat(container.findByPath(":other:task"), nullValue());
    }

    @Test
    public void canGetTaskByName() {
        Task task = addTask("task");

        assertThat(container.getByPath("task"), sameInstance(task));
    }

    @Test
    public void canGetTaskByPath() {
        Task task = addTask("task");
        expectTaskLookupInOtherProject(":a:b:c", "task", task);

        assertThat(container.getByPath(":a:b:c:task"), sameInstance(task));
    }

    @Test
    public void getByPathFailsForUnknownTask() {
        try {
            container.getByPath("unknown");
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task with path 'unknown' not found in <project>."));
        }
    }

    @Test
    public void resolveLocatesTaskByName() {
        Task task = addTask("1");

        assertThat(container.resolveTask(1), sameInstance(task));
    }

    @Test
    public void resolveLocatesTaskByPath() {
        Task task = addTask("task");
        expectTaskLookupInOtherProject(":", "task", task);
        assertThat(container.resolveTask(new StringBuilder(":task")), sameInstance(task));
    }
    
    private void expectTaskLookupInOtherProject(final String projectPath, final String taskName, final Task task) {
        context.checking(new Expectations() {{
            Project otherProject = context.mock(Project.class);
            TaskContainer otherTaskContainer = context.mock(TaskContainer.class);

            allowing(project).findProject(projectPath);
            will(returnValue(otherProject));

            allowing(otherProject).getTasks();
            will(returnValue(otherTaskContainer));

            allowing(otherTaskContainer).findByName(taskName);
            will(returnValue(task));
        }});
    }

    private TaskInternal task(final String name) {
        final TaskInternal task = context.mock(TaskInternal.class, "[task" + ++taskCount + "]");
        context.checking(new Expectations(){{
            allowing(task).getName();
            will(returnValue(name));
        }});
        return task;
    }

    private Task addTask(String name) {
        final Task task = task(name);
        final Map<String, ?> options = GUtil.map(Task.TASK_NAME, name);
        context.checking(new Expectations() {{
            one(taskFactory).createTask(project, options);
            will(returnValue(task));
        }});
        container.add(name);
        return task;
    }

}
