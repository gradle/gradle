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
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.TreeSet;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class NameResolvingTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Project project = context.mock(Project.class, "child");

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

        NameResolvingTaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("a:b"));
        executer.select(project);
        assertThat(executer.getDescription(), equalTo("primary task 'a:b'"));
        assertThat(executer.getTasks(), equalTo((Iterable<Task>) WrapUtil.toSet(task)));
    }

    @Test
    public void selectsAllTasksWithTheProvidedName() {
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        final Set<Task> tasks = WrapUtil.toSet(task1, task2);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name", true);
            will(returnValue(tasks));
        }});

        NameResolvingTaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("name"));
        executer.select(project);
        assertThat(executer.getDescription(), equalTo("primary task 'name'"));
        assertThat(executer.getTasks(), equalTo((Iterable<Task>) tasks));
    }

    @Test
    public void executesAllSelectedTasks() {
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        final DefaultTaskExecuter taskExecuter = context.mock(DefaultTaskExecuter.class);
        final Set<Task> tasks = WrapUtil.toSet(task1, task2);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name", true);
            will(returnValue(tasks));
            one(taskExecuter).execute(tasks);
            will(returnValue(false));
        }});

        NameResolvingTaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("name"));
        executer.select(project);
        executer.execute(taskExecuter);
        assertThat(executer.requiresProjectReload(), equalTo(false));
    }
    
    @Test
    public void treatsEachProvidedNameAsASeparateGroup() {
        final Project project1 = context.mock(Project.class, "project1");
        final Project project2 = context.mock(Project.class, "project2");
        final Task task1 = context.mock(Task.class, "task1");
        final Task task2 = context.mock(Task.class, "task2");

        context.checking(new Expectations() {{
            allowing(project1).getTasksByName(with(aNonNull(String.class)), with(equalTo(true)));
            will(returnValue(WrapUtil.toSet(task1)));
            atLeast(1).of(project2).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet(task2)));
        }});

        NameResolvingTaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("name1", "name2"));
        executer.select(project1);
        assertThat(executer.getDescription(), equalTo("primary task 'name1'"));
        assertThat(executer.getTasks(), equalTo((Iterable<Task>) WrapUtil.toSet(task1)));

        executer.select(project2);
        assertThat(executer.getDescription(), equalTo("primary task 'name2'"));
        assertThat(executer.getTasks(), equalTo((Iterable<Task>) WrapUtil.toSet(task2)));
    }

    @Test
    public void hasNoNextGroupOnceAllNamesHaveBeenVisited() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(WrapUtil.toSet(task)));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet(task)));
        }});

        NameResolvingTaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("name1", "name2"));

        assertThat(executer.hasNext(), equalTo(true));
        executer.select(project);

        assertThat(executer.hasNext(), equalTo(true));
        executer.select(project);

        assertThat(executer.hasNext(), equalTo(false));
        executer.select(project);
        assertThat(executer.getDescription(), equalTo(""));
        assertThat(executer.getTasks(), equalTo((Iterable<Task>) new TreeSet<Task>()));
    }

    @Test
    public void failsWhenUnknownTaskNameIsProvided() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(WrapUtil.toSet()));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet(task)));
        }});

        TaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("name1", "name2"));
        try {
            executer.select(project);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task 'name1' not found in this project."));
        }
    }

    @Test
    public void failsWhenUnknownTaskPathIsProvided() {
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).findTask("a:b");
            will(returnValue(null));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet(task)));
        }});

        TaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("a:b", "name2"));
        try {
            executer.select(project);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task 'a:b' not found in this project."));
        }
    }

    @Test
    public void failsWhenMultipleUnknownTaskNamesAreProvided() {
        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(WrapUtil.toSet()));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet()));
        }});

        TaskExecuter executer = new NameResolvingTaskExecuter(WrapUtil.toList("name1", "name2"));
        try {
            executer.select(project);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Tasks ['name1', 'name2'] not found in this project."));
        }
    }
}
