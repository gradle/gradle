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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class TaskNameResolvingBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ProjectInternal project = context.mock(AbstractProject.class, "[project]");
    private final ProjectInternal otherProject = context.mock(AbstractProject.class, "[otherProject]");
    private final ProjectInternal rootProject = context.mock(ProjectInternal.class);
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final TaskGraphExecuter taskExecuter = context.mock(TaskGraphExecuter.class);
    private final TaskNameResolver resolver = context.mock(TaskNameResolver.class);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(gradle).getDefaultProject();
            will(returnValue(project));
            allowing(gradle).getTaskGraph();
            will(returnValue(taskExecuter));
            allowing(project).getAllprojects();
            will(returnValue(toSet(project, otherProject)));
            allowing(otherProject).getPath();
            will(returnValue(":anotherProject"));
            allowing(rootProject).getPath();
            will(returnValue(":"));
        }});
    }

    @Test
    public void selectsAllTasksWithTheProvidedNameInCurrentProjectAndSubprojects() {
        final Task task1 = task("name");
        final Task task2 = task("name");
        final Task task3 = task("other");

        context.checking(new Expectations() {{
            one(resolver).selectAll("name", project);
            will(returnValue(tasks(task1, task2, task3)));
            one(taskExecuter).addTasks(toSet(task1, task2));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"), resolver);
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

    private void assertMatches(final String pattern, final String matches, String... otherNames) {
        final Set<Task> tasks = new HashSet<Task>();
        final Task task1 = task(matches);
        tasks.add(task1);
        final Task task2 = task(matches);
        tasks.add(task2);
        for (String name : otherNames) {
            tasks.add(task(name));
        }
        tasks.add(task("."));
        tasks.add(task("other"));

        context.checking(new Expectations() {{
            one(resolver).selectAll(pattern, project);
            will(returnValue(tasks(tasks)));
            one(taskExecuter).addTasks(toSet(task1, task2));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList(pattern), resolver);
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo(String.format("primary task '%s'", matches)));
    }
    
    @Test
    public void selectsTaskWithMatchingRelativePath() {
        final Task task1 = task("b");
        final Task task2 = task("a");

        context.checking(new Expectations(){{
            one(project).getChildProjects();
            will(returnValue(toMap("a", otherProject)));
            one(resolver).select("b", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b"), resolver);
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task 'a:b'"));
    }

    @Test
    public void selectsTaskWithMatchingTaskInRootProject() {
        final Task task1 = task("b");
        final Task task2 = task("a");

        context.checking(new Expectations(){{
            one(project).getRootProject();
            will(returnValue(rootProject));
            one(resolver).select("b", rootProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList(":b"), resolver);
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':b'"));
    }

    @Test
    public void selectsTaskWithMatchingAbsolutePath() {
        final Task task1 = task("b");
        final Task task2 = task("a");

        context.checking(new Expectations(){{
            one(project).getRootProject();
            will(returnValue(rootProject));
            one(rootProject).getChildProjects();
            will(returnValue(toMap("a", otherProject)));
            one(resolver).select("b", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList(":a:b"), resolver);
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':a:b'"));
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectTasksWhenNoExactMatchAndPathProvided() {
        final Task task1 = task("someTask");
        final Task task2 = task("other");

        context.checking(new Expectations(){{
            one(project).getChildProjects();
            will(returnValue(toMap("anotherProject", otherProject)));
            one(resolver).select("soTa", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("anotherProject:soTa"), resolver);
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':anotherProject:someTask'"));
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectProjectWhenPathProvided() {
        final Task task1 = task("someTask");
        final Task task2 = task("other");

        context.checking(new Expectations(){{
            one(project).getChildProjects();
            will(returnValue(toMap("anotherProject", otherProject)));
            one(resolver).select("soTa", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("anPr:soTa"), resolver);
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary task ':anotherProject:someTask'"));
    }

    @Test
    public void failsWhenProvidedTaskNameIsAmbiguous() {
        final Task task1 = task("someTask");
        final Task task2 = task("someTasks");

        context.checking(new Expectations() {{
            one(resolver).selectAll("soTa", project);
            will(returnValue(tasks(task1, task2)));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("soTa"), resolver);
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
            one(resolver).selectAll("ssomeTask", project);
            will(returnValue(tasks(task1, task2, task3, task4)));
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("ssomeTask"), resolver);
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
            one(resolver).selectAll("name", project);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1, task2));
            one(taskExecuter).execute();
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("name"), resolver);
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
            one(resolver).select("name1", otherProject);
            will(returnValue(tasks(task1)));
            one(resolver).selectAll("name2", project);
            will(returnValue(tasks(task2)));

            Sequence sequence = context.sequence("tasks");

            one(taskExecuter).addTasks(toSet(task1));
            inSequence(sequence);

            one(taskExecuter).addTasks(toSet(task2));
            inSequence(sequence);

            one(taskExecuter).execute();
            inSequence(sequence);
        }});

        TaskNameResolvingBuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("child:name1", "name2"), resolver);
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("primary tasks 'child:name1', 'name2'"));
        executer.execute();
    }

    @Test
    public void failsWhenUnknownTaskNameIsProvided() {
        final Task task1 = task("t1");
        final Task task2 = task("t2");

        context.checking(new Expectations() {{
            one(resolver).selectAll("b3", project);
            will(returnValue(tasks(task1, task2)));
        }});

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("b3"), resolver);
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

        BuildExecuter executer = new TaskNameResolvingBuildExecuter(toList("a:b", "name2"), resolver);
        try {
            executer.select(gradle);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("Project 'a' is ambiguous in [project]. Candidates are: 'aa', 'ab'."));
        }
    }

    private Task task(final String name) {
        final Task task = context.mock(Task.class);
        context.checking(new Expectations(){{
            allowing(task).getName();
            will(returnValue(name));
        }});
        return task;
    }

    private Multimap<String, Task> tasks(Task... tasks) {
        return tasks(Arrays.asList(tasks));
    }

    private Multimap<String, Task> tasks(Iterable<Task> tasks) {
        Multimap<String, Task> map = LinkedHashMultimap.create();
        for (Task task : tasks) {
            map.put(task.getName(), task);
        }
        return map;
    }

}
