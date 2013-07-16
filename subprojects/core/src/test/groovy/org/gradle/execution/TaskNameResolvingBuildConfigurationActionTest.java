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
import org.gradle.StartParameter;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.CommandLineOption;
import org.gradle.util.GUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class TaskNameResolvingBuildConfigurationActionTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ProjectInternal project = context.mock(AbstractProject.class, "[project]");
    private final ProjectInternal otherProject = context.mock(AbstractProject.class, "[otherProject]");
    private final ProjectInternal rootProject = context.mock(ProjectInternal.class);
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final TaskGraphExecuter taskExecuter = context.mock(TaskGraphExecuter.class);
    private final TaskNameResolver resolver = context.mock(TaskNameResolver.class);
    private final BuildExecutionContext executionContext = context.mock(BuildExecutionContext.class);
    private final StartParameter startParameter = context.mock(StartParameter.class);
    private final TaskNameResolvingBuildConfigurationAction action = new TaskNameResolvingBuildConfigurationAction(resolver);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(executionContext).getGradle();
            will(returnValue(gradle));
            allowing(gradle).getDefaultProject();
            will(returnValue(project));
            allowing(gradle).getTaskGraph();
            will(returnValue(taskExecuter));
            allowing(gradle).getStartParameter();
            will(returnValue(startParameter));
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
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("name")));

            one(resolver).selectAll("name", project);
            will(returnValue(tasks(task1, task2, task3)));
            one(taskExecuter).addTasks(toSet(task1, task2));
            one(executionContext).proceed();
        }});

        action.configure(executionContext);
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectTasksWhenNoExactMatch() {
        assertMatches("soTaWN", "someTaskWithName", "saTaWN");
        assertMatches("ta1", "task1", "Task1", "T1", "t2");
        assertMatches("t1", "t1extra");
        assertMatches("t1", "t12");
        assertMatches("t1", "task1extra", "task2extra");
        assertMatches("ABC", "AbcBbcCdc", "aabbcc");
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
            one(startParameter).getTaskNames();
            will(returnValue(toList(pattern)));

            one(resolver).selectAll(pattern, project);
            will(returnValue(tasks(tasks)));
            one(taskExecuter).addTasks(toSet(task1, task2));
            one(executionContext).proceed();
        }});

        action.configure(executionContext);
    }
    
    @Test
    public void selectsTaskWithMatchingRelativePath() {
        final Task task1 = task("b");
        final Task task2 = task("a");

        context.checking(new Expectations(){{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("a:b")));

            one(project).getChildProjects();
            will(returnValue(toMap("a", otherProject)));
            one(resolver).select("b", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
            one(executionContext).proceed();
        }});

        action.configure(executionContext);
    }

    @Test
    public void selectsTaskWithMatchingTaskInRootProject() {
        final Task task1 = task("b");
        final Task task2 = task("a");

        context.checking(new Expectations(){{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList(":b")));

            one(project).getRootProject();
            will(returnValue(rootProject));
            one(resolver).select("b", rootProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
            one(executionContext).proceed();
        }});

        action.configure(executionContext);
    }

    @Test
    public void selectsTaskWithMatchingAbsolutePath() {
        final Task task1 = task("b");
        final Task task2 = task("a");

        context.checking(new Expectations(){{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList(":a:b")));

            one(project).getRootProject();
            will(returnValue(rootProject));
            one(rootProject).getChildProjects();
            will(returnValue(toMap("a", otherProject)));
            one(resolver).select("b", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
            one(executionContext).proceed();
        }});

        action.configure(executionContext);
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectTasksWhenNoExactMatchAndPathProvided() {
        final Task task1 = task("someTask");
        final Task task2 = task("other");

        context.checking(new Expectations(){{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("anotherProject:soTa")));

            one(project).getChildProjects();
            will(returnValue(toMap("anotherProject", otherProject)));
            one(resolver).select("soTa", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
            one(executionContext).proceed();
        }});

        action.configure(executionContext);
    }

    @Test
    public void usesCamelCaseAbbreviationToSelectProjectWhenPathProvided() {
        final Task task1 = task("someTask");
        final Task task2 = task("other");

        context.checking(new Expectations(){{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("anPr:soTa")));

            one(project).getChildProjects();
            will(returnValue(toMap("anotherProject", otherProject)));
            one(resolver).select("soTa", otherProject);
            will(returnValue(tasks(task1, task2)));
            one(taskExecuter).addTasks(toSet(task1));
            one(executionContext).proceed();
        }});

        action.configure(executionContext);
    }

    @Test
    public void failsWhenProvidedTaskNameIsAmbiguous() {
        final Task task1 = task("someTask");
        final Task task2 = task("someTasks");

        context.checking(new Expectations() {{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("soTa")));

            one(resolver).selectAll("soTa", project);
            will(returnValue(tasks(task1, task2)));
        }});

        try {
            action.configure(executionContext);
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
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("ssomeTask")));

            one(resolver).selectAll("ssomeTask", project);
            will(returnValue(tasks(task1, task2, task3, task4)));
        }});

        try {
            action.configure(executionContext);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("Task 'ssomeTask' not found in [project]. Some candidates are: 'someTask', 'someTasks', 'sometask'."));
        }
    }

    @Test
    public void treatsEachProvidedNameAsASeparateGroup() {
        final Task task1 = task("name1");
        final Task task2 = task("name2");

        context.checking(new Expectations() {{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("child:name1", "name2")));

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

            ignoring(executionContext);
        }});

        action.configure(executionContext);
    }

    @Test
    public void canConfigureSingleTaskUsingCommandLineOptions() {
        final TaskWithBooleanProperty task1 = task("name1", TaskWithBooleanProperty.class);
        final Task task2 = task("name2");

        context.checking(new Expectations() {{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("name1", "--all", "name2")));

            one(resolver).selectAll("name1", project);
            will(returnValue(tasks(task1)));
            one(resolver).selectAll("name2", project);
            will(returnValue(tasks(task2)));

            Sequence sequence = context.sequence("tasks");

            one(task1).setSomeFlag(true);

            one(taskExecuter).addTasks(toSet(task1));
            inSequence(sequence);

            one(taskExecuter).addTasks(toSet(task2));
            inSequence(sequence);

            ignoring(executionContext);
        }});

        action.configure(executionContext);
    }

    @Test
    public void failsWhenUnknownTaskNameIsProvided() {
        final Task task1 = task("t1");
        final Task task2 = task("t2");

        context.checking(new Expectations() {{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("b3")));

            one(resolver).selectAll("b3", project);
            will(returnValue(tasks(task1, task2)));
        }});

        try {
            action.configure(executionContext);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("Task 'b3' not found in [project]."));
        }
    }

    @Test
    public void failsWhenCannotFindProjectInPath() {
        context.checking(new Expectations() {{
            allowing(startParameter).getTaskNames();
            will(returnValue(toList("a:b", "name2")));

            one(project).getChildProjects();
            will(returnValue(GUtil.map("aa", otherProject, "ab", otherProject)));
        }});

        try {
            action.configure(executionContext);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Project 'a' is ambiguous in [project]. Candidates are: 'aa', 'ab'."));
        }
    }

    private Task task(String name) {
        return task(name, Task.class);
    }

    private <T extends Task> T task(final String name, Class<T> taskType) {
        final T task = context.mock(taskType);
        context.checking(new Expectations(){{
            allowing(task).getName();
            will(returnValue(name));
        }});
        return task;
    }

    private Multimap<String, TaskSelectionResult> tasks(Task... tasks) {
        return tasks(Arrays.asList(tasks));
    }

    private Multimap<String, TaskSelectionResult> tasks(Iterable<Task> tasks) {
        Multimap<String, TaskSelectionResult> map = LinkedHashMultimap.create();
        for (final Task task : tasks) {
            map.put(task.getName(), new TaskNameResolver.SimpleTaskSelectionResult(task));
        }
        return map;
    }

    public abstract class TaskWithBooleanProperty implements Task {
        @CommandLineOption(options = "all", description = "Some boolean flag")
        public void setSomeFlag(boolean flag) { }
    }
}
