/*
 * Copyright 2008 the original author or authors.
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
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class TaskNameResolvingBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ProjectInternal project = context.mock(ProjectInternal.class, "[project]");
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final TaskContainer taskContainer = context.mock(TaskContainer.class);
    private final TaskExecuter taskExecuter = context.mock(TaskExecuter.class);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(gradle).getDefaultProject();
            will(returnValue(project));
            allowing(gradle).getTaskGraph();
            will(returnValue(taskExecuter));
            allowing(project).getTasks();
            will(returnValue(taskContainer));
        }});
    }
    
    @Test
    public void selectsTaskWithMatchingPath() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations(){{
            atLeast(1).of(taskContainer).findByPath("a:b");
            will(returnValue(task));
            one(taskExecuter).addTasks(toLinkedSet(task));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task 'a:b'"));
    }

    @Test
    public void selectsAllTasksWithTheProvidedName() {
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        final Set<Task> tasks = toSet(task1, task2);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name", true);
            will(returnValue(tasks));
            one(taskExecuter).addTasks(tasks);
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task 'name'"));
    }

    @Test
    public void executesAllSelectedTasks() {
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        final Set<Task> tasks = toSet(task1, task2);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name", true);
            will(returnValue(tasks));
            one(taskExecuter).addTasks(tasks);
            one(taskExecuter).execute();
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"));
        executer.select(gradle);
        executer.execute();
    }
    
    @Test
    public void treatsEachProvidedNameAsASeparateGroup() {
        final Task task1 = context.mock(Task.class, "task1");
        final Task task2 = context.mock(Task.class, "task2");

        context.checking(new Expectations() {{
            allowing(project).getTasksByName("name1", true);
            will(returnValue(toSet(task1)));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(toSet(task2)));

            Sequence sequence = context.sequence("tasks");

            one(taskExecuter).addTasks(toSet(task1));
            inSequence(sequence);

            one(taskExecuter).addTasks(toSet(task2));
            inSequence(sequence);

            one(taskExecuter).execute();
            inSequence(sequence);
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name1", "name2"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary tasks 'name1', 'name2'"));
        executer.execute();
    }

    @Test
    public void failsWhenUnknownTaskNameIsProvided() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(toSet()));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(toSet(task)));
        }});

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name1", "name2"));
        try {
            executer.select(gradle);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task 'name1' not found in [project]."));
        }
    }

    @Test
    public void failsWhenUnknownTaskPathIsProvided() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(taskContainer).findByPath("a:b");
            will(returnValue(null));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(toSet(task)));
        }});

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b", "name2"));
        try {
            executer.select(gradle);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task 'a:b' not found in [project]."));
        }
    }

    @Test
    public void failsWhenMultipleUnknownTaskNamesAreProvided() {
        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(toSet()));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(toSet()));
        }});

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name1", "name2"));
        try {
            executer.select(gradle);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Tasks 'name1', 'name2' not found in [project]."));
        }
    }
}
