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
package org.gradle.execution;

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.gradle.util.GUtil;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class TaskNameResolvingBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ProjectInternal project = context.mock(ProjectInternal.class, "[project]");
    private final ProjectInternal otherProject = context.mock(ProjectInternal.class, "[otherProject]");
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final TaskContainerInternal taskContainer = context.mock(TaskContainerInternal.class, "[projectTasks]");
    private final TaskContainerInternal otherProjectTaskContainer = context.mock(TaskContainerInternal.class, "[otherProjectTasks]");
    private final TaskGraphExecuter taskExecuter = context.mock(TaskGraphExecuter.class);
    private int counter;

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(gradle).getDefaultProject();
            will(returnValue(project));
            allowing(gradle).getTaskGraph();
            will(returnValue(taskExecuter));
            allowing(project).getTasks();
            will(returnValue(taskContainer));
            allowing(project).getAllprojects();
            will(returnValue(toSet(project, otherProject)));
            allowing(otherProject).getTasks();
            will(returnValue(otherProjectTaskContainer));
        }});
    }

    @Test
    public void selectsAllTasksWithTheProvidedNameInCurrentProjectAndSubprojects() {
        final Task task1 = task("name");
        final Task task2 = task("name");

        context.checking(new Expectations() {{
            one(project).getTasksByName("name", true);
            will(returnValue(toSet(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1, task2));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task 'name'"));
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectTasksWhenNoExactMatch() {
        assertMatches("soTaWN", "someTaskWithName", "saTaWN");
        assertMatches("t1", "task1", "Task1", "T1", "t2");
        assertMatches("t1", "t1extra");
        assertMatches("t1", "t12");
        assertMatches("t1", "task1extra", "task2extra");
        assertMatches("ABC", "AbcBbcCdc", "abc");
        assertMatches("s-t", "some-task");
        assertMatches("s t", "some task");
        assertMatches("s.t", "some.task");
        assertMatches("a\\De(", "abc\\Def(", "a\\Df(");
    }

    private void assertMatches(final String pattern, String matches, String... otherNames) {
        final Task task1 = task(matches);
        final Task task2 = task(matches);
        final Set<Task> tasks = new HashSet<Task>();
        tasks.add(task2);
        for (String name : otherNames) {
            tasks.add(task(name));
        }
        tasks.add(task("."));
        tasks.add(task("other"));

        context.checking(new Expectations() {{
            one(project).getTasksByName(pattern, true);
            will(returnValue(toSet()));
            one(taskContainer).getAll();
            will(returnValue(toSet(task1)));
            one(otherProjectTaskContainer).getAll();
            will(returnValue(tasks));
            one(taskExecuter).addTasks(toSet(task1, task2));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList(pattern));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo(String.format("primary task '%s'", matches)));
    }
    
    @Test
    public void selectsTaskWithMatchingRelativePath() {
        final Task task1 = task("b");

        context.checking(new Expectations(){{
            one(project).getChildProjects();
            will(returnValue(toMap("a", otherProject)));
            one(otherProjectTaskContainer).findByName("b");
            will(returnValue(task1));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task 'a:b'"));
    }

    @Test
    public void selectsTaskWithMatchingTaskInRootProject() {
        final Task task1 = task("b");

        context.checking(new Expectations(){{
            one(project).getRootProject();
            will(returnValue(otherProject));
            one(otherProjectTaskContainer).findByName("b");
            will(returnValue(task1));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList(":b"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':b'"));
    }

    @Test
    public void selectsTaskWithMatchingAbsolutePath() {
        final Task task1 = task("b");

        context.checking(new Expectations(){{
            one(project).getRootProject();
            will(returnValue(otherProject));
            one(otherProject).getChildProjects();
            will(returnValue(toMap("a", otherProject)));
            one(otherProjectTaskContainer).findByName("b");
            will(returnValue(task1));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList(":a:b"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':a:b'"));
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectTasksWhenNoExactMatchAndPathProvided() {
        final Task task1 = task("someTask");
        final Task task2 = task("other");

        context.checking(new Expectations(){{
            one(project).getChildProjects();
            will(returnValue(toMap("a", otherProject)));
            one(otherProjectTaskContainer).findByName("soTa");
            will(returnValue(null));
            one(otherProjectTaskContainer).getAll();
            will(returnValue(toSet(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
            allowing(otherProject).getPath();
            will(returnValue(":a"));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:soTa"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':a:someTask'"));
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectProjectWhenPathProvided() {
        final Task task1 = task("someTask");
        final Task task2 = task("other");

        context.checking(new Expectations(){{
            one(project).getChildProjects();
            will(returnValue(toMap("someProject", otherProject)));
            one(otherProjectTaskContainer).findByName("soTa");
            will(returnValue(null));
            one(otherProjectTaskContainer).getAll();
            will(returnValue(toSet(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
            allowing(otherProject).getPath();
            will(returnValue(":someProject"));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("soPr:soTa"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':someProject:someTask'"));
    }

    @Test
    public void failsWhenProvidedTaskNameIsAmbiguous() {
        final Task task1 = task("someTask");
        final Task task2 = task("someTasks");

        context.checking(new Expectations() {{
            one(project).getTasksByName("soTa", true);
            will(returnValue(toSet()));
            one(taskContainer).getAll();
            will(returnValue(toSet(task1)));
            one(otherProjectTaskContainer).getAll();
            will(returnValue(toSet(task2)));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("soTa"));
        try {
            executer.select(gradle);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("Task 'soTa' is ambiguous in [project]. Candidates are: 'someTask', 'someTasks'."));
        }
    }

    @Test
    public void reportsTyposInTaskName() {
        final Task task1 = task("someTask");
        final Task task2 = task("someTasks");
        final Task task3 = task("sometask");
        final Task task4 = task("other");

        context.checking(new Expectations() {{
            one(project).getTasksByName("ssomeTask", true);
            will(returnValue(toSet()));
            one(taskContainer).getAll();
            will(returnValue(toSet(task1, task2)));
            one(otherProjectTaskContainer).getAll();
            will(returnValue(toSet(task3, task4)));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("ssomeTask"));
        try {
            executer.select(gradle);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("Task 'ssomeTask' not found in [project]. Some candidates are: 'someTask', 'someTasks', 'sometask'."));
        }
    }

    @Test
    public void executesAllSelectedTasks() {
        final Task task1 = task("name");
        final Task task2 = task("name");

        context.checking(new Expectations() {{
            one(project).getTasksByName("name", true);
            will(returnValue(toSet(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1, task2));
            one(taskExecuter).execute();
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"));
        executer.select(gradle);
        executer.execute();
    }
    
    @Test
    public void treatsEachProvidedNameAsASeparateGroup() {
        final Task task1 = task("name1");
        final Task task2 = task("name2");

        context.checking(new Expectations() {{
            one(project).getChildProjects();
            will(returnValue(toMap("child", otherProject)));
            one(otherProjectTaskContainer).findByName("name1");
            will(returnValue(task1));
            one(project).getTasksByName("name2", true);
            will(returnValue(toSet(task2)));

            Sequence sequence = context.sequence("tasks");

            one(taskExecuter).addTasks(toSet(task1));
            inSequence(sequence);

            one(taskExecuter).addTasks(toSet(task2));
            inSequence(sequence);

            one(taskExecuter).execute();
            inSequence(sequence);
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("child:name1", "name2"));
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary tasks 'child:name1', 'name2'"));
        executer.execute();
    }

    @Test
    public void failsWhenUnknownTaskNameIsProvided() {
        final Task task1 = task("t1");
        final Task task2 = task("t2");

        context.checking(new Expectations() {{
            one(project).getTasksByName("b3", true);
            will(returnValue(toSet()));
            one(taskContainer).getAll();
            will(returnValue(toSet(task1, task2)));
            one(otherProjectTaskContainer).getAll();
            will(returnValue(toSet()));
        }});

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("b3"));
        try {
            executer.select(gradle);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("Task 'b3' not found in [project]."));
        }
    }

    @Test
    public void failsWhenCannotFindProjectInPath() {
        context.checking(new Expectations() {{
            one(project).getChildProjects();
            will(returnValue(GUtil.map("aa", otherProject, "ab", otherProject)));
        }});

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b", "name2"));
        try {
            executer.select(gradle);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("Project 'a' is ambiguous in [project]. Candidates are: 'aa', 'ab'."));
        }
    }

    private Task task(final String name) {
        final Task task = context.mock(Task.class, "task" + counter++ + "_" + name);
        context.checking(new Expectations(){{
            allowing(task).getName();
            will(returnValue(name));
        }});
        return task;
    }
}
