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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class TaskNameResolvingBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Project project = context.mock(Project.class, "[project]");

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
    }
    
    @Test
    public void selectsTaskWithMatchingPath() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations(){{
            atLeast(1).of(project).findTask("a:b");
            will(returnValue(task));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b"));
        executer.select(project);
        assertThat(executer.getDisplayName(), equalTo("primary task 'a:b'"));
        assertThat(executer.getTasks(), equalTo((Iterable<Task>) toSet(task)));
    }

    @Test
    public void selectsAllTasksWithTheProvidedName() {
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        final Set<Task> tasks = toSet(task1, task2);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name", true);
            will(returnValue(tasks));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"));
        executer.select(project);
        assertThat(executer.getDisplayName(), equalTo("primary task 'name'"));
        assertThat(executer.getTasks(), equalTo((Iterable<Task>) tasks));
    }

    @Test
    public void executesAllSelectedTasks() {
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        final TaskExecuter taskExecuter = context.mock(TaskExecuter.class);
        final Set<Task> tasks = toSet(task1, task2);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name", true);
            will(returnValue(tasks));
            one(taskExecuter).addTasks(tasks);
            one(taskExecuter).execute();
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"));
        executer.select(project);
        executer.execute(taskExecuter);
    }
    
    @Test
    public void treatsEachProvidedNameAsASeparateGroup() {
        final TaskExecuter taskExecuter = context.mock(TaskExecuter.class);
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
        executer.select(project);
        assertThat(executer.getDisplayName(), equalTo("primary tasks 'name1', 'name2'"));
        executer.execute(taskExecuter);
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
            executer.select(project);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task 'name1' not found in [project]."));
        }
    }

    @Test
    public void failsWhenUnknownTaskPathIsProvided() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).findTask("a:b");
            will(returnValue(null));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(toSet(task)));
        }});

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b", "name2"));
        try {
            executer.select(project);
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
            executer.select(project);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Tasks 'name1', 'name2' not found in [project]."));
        }
    }
}
