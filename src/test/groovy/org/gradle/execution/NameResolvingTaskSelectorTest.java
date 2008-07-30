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
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class NameResolvingTaskSelectorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void selectsTaskWithMatchingPath() {
        final Project project = context.mock(Project.class, "child");
        final Project rootProject = context.mock(Project.class, "root");
        final Task task = context.mock(Task.class);
        final SortedMap<Project, Set<Task>> tasks = WrapUtil.toSortedMap(project, WrapUtil.toSet(task));

        context.checking(new Expectations(){{
            atLeast(1).of(project).absolutePath("a:b");
            will(returnValue(":a:b"));

            atLeast(1).of(project).getRootProject();
            will(returnValue(rootProject));

            atLeast(1).of(rootProject).getAllTasks(true);
            will(returnValue(tasks));

            allowing(task).getPath();
            will(returnValue(":a:b"));
        }});

        TaskSelector selector = new NameResolvingTaskSelector(WrapUtil.toList("a:b"));
        selector.select(project);
        assertThat(selector.getDescription(), equalTo("primary task 'a:b'"));
        assertThat(selector.getTasks(), equalTo((Iterable<Task>) WrapUtil.toSet(task)));
    }

    @Test
    public void selectsAllTasksWithTheProvidedName() {
        final Project project = context.mock(Project.class);
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        final Set<Task> tasks = WrapUtil.toSet(task1, task2);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name", true);
            will(returnValue(tasks));
        }});

        TaskSelector selector = new NameResolvingTaskSelector(WrapUtil.toList("name"));
        selector.select(project);
        assertThat(selector.getDescription(), equalTo("primary task 'name'"));
        assertThat(selector.getTasks(), equalTo((Iterable<Task>) tasks));
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

        TaskSelector selector = new NameResolvingTaskSelector(WrapUtil.toList("name1", "name2"));
        selector.select(project1);
        assertThat(selector.getDescription(), equalTo("primary task 'name1'"));
        assertThat(selector.getTasks(), equalTo((Iterable<Task>) WrapUtil.toSet(task1)));

        selector.select(project2);
        assertThat(selector.getDescription(), equalTo("primary task 'name2'"));
        assertThat(selector.getTasks(), equalTo((Iterable<Task>) WrapUtil.toSet(task2)));
    }

    @Test
    public void hasNoNextGroupOnceAllNamesHaveBeenVisited() {
        final Project project = context.mock(Project.class);
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(WrapUtil.toSet(task)));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet(task)));
        }});

        TaskSelector selector = new NameResolvingTaskSelector(WrapUtil.toList("name1", "name2"));

        assertThat(selector.hasNext(), equalTo(true));
        selector.select(project);

        assertThat(selector.hasNext(), equalTo(true));
        selector.select(project);

        assertThat(selector.hasNext(), equalTo(false));
        selector.select(project);
        assertThat(selector.getDescription(), equalTo(""));
        assertThat(selector.getTasks(), equalTo((Iterable<Task>) new TreeSet<Task>()));
    }

    @Test
    public void failsWhenUnknownTaskNameIsProvided() {
        final Project project = context.mock(Project.class);
        final Task task = context.mock(Task.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(WrapUtil.toSet()));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet(task)));
        }});

        TaskSelector selector = new NameResolvingTaskSelector(WrapUtil.toList("name1", "name2"));
        try {
            selector.select(project);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Task 'name1' not found in this project."));
        }
    }

    @Test
    public void failsWhenMultipleUnknownTaskNamesAreProvided() {
        final Project project = context.mock(Project.class);

        context.checking(new Expectations() {{
            atLeast(1).of(project).getTasksByName("name1", true);
            will(returnValue(WrapUtil.toSet()));
            atLeast(1).of(project).getTasksByName("name2", true);
            will(returnValue(WrapUtil.toSet()));
        }});

        TaskSelector selector = new NameResolvingTaskSelector(WrapUtil.toList("name1", "name2"));
        try {
            selector.select(project);
            fail();
        } catch (UnknownTaskException e) {
            assertThat(e.getMessage(), equalTo("Tasks ['name1', 'name2'] not found in this project."));
        }
    }
}
