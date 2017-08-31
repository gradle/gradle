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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Rule;
import org.gradle.api.Task;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.diagnostics.internal.TaskDetails;
import org.gradle.api.tasks.diagnostics.internal.TaskReportRenderer;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.Path;
import org.gradle.util.TestUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import static org.gradle.util.WrapUtil.*;

@RunWith(JMock.class)
public class TaskReportTaskTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final TaskReportRenderer renderer = context.mock(TaskReportRenderer.class);
    private final ProjectInternal project = context.mock(DefaultProject.class);
    private final TaskContainerInternal taskContainer = context.mock(TaskContainerInternal.class);
    private final TaskContainerInternal implicitTasks = context.mock(TaskContainerInternal.class);
    private TaskReportTask task;

    @org.junit.Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider();

    @Before
    public void setup() {
        context.checking(new Expectations(){{
            allowing(project).absoluteProjectPath("list");
            will(returnValue(":path"));
            allowing(project).getTasks();
            will(returnValue(taskContainer));
            allowing(project).getConvention();
            will(returnValue(null));
            allowing(project).getAllprojects();
            will(returnValue(toSet(project)));
            allowing(project).getSubprojects();
            will(returnValue(toSet()));
        }});

        task = TestUtil.create(temporaryFolder).task(TaskReportTask.class);
        task.setRenderer(renderer);
    }

    @Test
    public void groupsTasksByTaskGroupAndPassesTasksToTheRenderer() throws IOException {
        context.checking(new Expectations() {{
            Task task1 = task("a", "group a");
            Task task2 = task("b", "group b");
            Task task3 = task("c");
            Task task4 = task("d", "group b", task3);

            List<String> testDefaultTasks = toList("defaultTask1", "defaultTask2");
            allowing(project).getDefaultTasks();
            will(returnValue(testDefaultTasks));

            allowing(taskContainer).realize();

            allowing(taskContainer).size();
            will(returnValue(4));

            allowing(taskContainer).iterator();
            will(returnIterator(toLinkedSet(task2, task3, task4, task1)));

            allowing(implicitTasks).iterator();
            will(returnIterator(toLinkedSet()));

            allowing(taskContainer).getRules();
            will(returnValue(toList()));

            Sequence sequence = context.sequence("seq");

            one(renderer).showDetail(false);
            inSequence(sequence);

            one(renderer).addDefaultTasks(testDefaultTasks);
            inSequence(sequence);

            one(renderer).startTaskGroup("group a");
            inSequence(sequence);

            one(renderer).addTask(with(isTask(task1)));
            inSequence(sequence);

            one(renderer).startTaskGroup("group b");
            inSequence(sequence);

            one(renderer).addTask(with(isTask(task2)));
            inSequence(sequence);

            one(renderer).addTask(with(isTask(task4)));
            inSequence(sequence);

            one(renderer).completeTasks();
            inSequence(sequence);
        }});

        task.generate(project);
    }

    @Test
    public void passesEachRuleToRenderer() throws IOException {
        context.checking(new Expectations() {{
            Rule rule1 = context.mock(Rule.class);
            Rule rule2 = context.mock(Rule.class);

            List<String> defaultTasks = toList();
            allowing(project).getDefaultTasks();
            will(returnValue(defaultTasks));

            allowing(taskContainer).realize();

            allowing(taskContainer).size();
            will(returnValue(0));

            allowing(taskContainer).iterator();
            will(returnIterator(toLinkedSet()));

            allowing(implicitTasks).iterator();
            will(returnIterator(toLinkedSet()));

            one(taskContainer).getRules();
            will(returnValue(toList(rule1, rule2)));

            Sequence sequence = context.sequence("seq");

            one(renderer).showDetail(false);
            inSequence(sequence);

            one(renderer).addDefaultTasks(defaultTasks);
            inSequence(sequence);

            one(renderer).completeTasks();
            inSequence(sequence);

            one(renderer).addRule(rule1);
            inSequence(sequence);

            one(renderer).addRule(rule2);
            inSequence(sequence);
        }});

        task.generate(project);
    }

    private Matcher<TaskDetails> isTask(final Task task) {
        return new BaseMatcher<TaskDetails>() {
            public boolean matches(Object o) {
                TaskDetails other = (TaskDetails) o;
                return other.getPath().equals(Path.path(task.getName()));
            }

            public void describeTo(Description description) {
                description.appendText("is ").appendValue(task);
            }
        };
    }

    private Task task(String name) {
        return task(name, null);
    }

    private Task task(final String name, final String taskGroup, final Task... dependencies) {
        final Task task = context.mock(Task.class);
        context.checking(new Expectations() {{
            allowing(task).getName();
            will(returnValue(name));
            allowing(task).getPath();
            will(returnValue(':' + name));
            allowing(task).getProject();
            will(returnValue(project));
            allowing(project).relativeProjectPath(':' + name);
            will(returnValue(name));
            allowing(task).getGroup();
            will(returnValue(taskGroup));
            allowing(task).compareTo(with(Matchers.notNullValue(Task.class)));
            will(new Action() {
                public Object invoke(Invocation invocation) throws Throwable {
                    Task other = (Task) invocation.getParameter(0);
                    return name.compareTo(other.getName());
                }

                public void describeTo(Description description) {
                    description.appendText("compare to");
                }
            });

            TaskDependency dependency = context.mock(TaskDependency.class);
            allowing(task).getTaskDependencies();
            will(returnValue(dependency));

            allowing(dependency).getDependencies(task);
            will(returnValue(toSet(dependencies)));
        }});

        return task;
    }
}
