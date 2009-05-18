/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.CircularReferenceException;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.GradleScriptException;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.DefaultTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import static org.gradle.util.HelperUtil.*;
import org.gradle.util.TestClosure;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultTaskExecuterTest {
    static File TEST_ROOT_DIR = new File("/path/root");

    TaskExecuter taskExecuter;
    ProjectInternal root;
    JUnit4Mockery context = new JUnit4Mockery();
    List<Task> executedTasks = new ArrayList<Task>();

    @Before
    public void setUp() {
        root = createRootProject(new File("root"));
        taskExecuter = new DefaultTaskExecuter();
    }

    @Test
    public void testExecutesTasksInDependencyOrder() {
        Task a = createTask("a");
        Task b = createTask("b", a);
        Task c = createTask("c", b, a);
        Task d = createTask("d", c);

        taskExecuter.execute(toList(d));

        assertThat(executedTasks, equalTo(toList(a, b, c, d)));
    }

    @Test
    public void testExecutesDependenciesInNameOrder() {
        Task a = createTask("a");
        Task b = createTask("b");
        Task c = createTask("c");
        Task d = createTask("d", b, a, c);

        taskExecuter.execute(toList(d));

        assertThat(executedTasks, equalTo(toList(a, b, c, d)));
    }

    @Test
    public void testExecutesTasksInASingleBatchInNameOrder() {
        Task a = createTask("a");
        Task b = createTask("b");
        Task c = createTask("c");

        taskExecuter.execute(toList(b, c, a));

        assertThat(executedTasks, equalTo(toList(a, b, c)));
    }

    @Test
    public void testExecutesBatchesInOrderAdded() {
        Task a = createTask("a");
        Task b = createTask("b");
        Task c = createTask("c");
        Task d = createTask("d");

        taskExecuter.addTasks(toList(c, b));
        taskExecuter.addTasks(toList(d, a));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(b, c, a, d)));
    }

    @Test
    public void testExecutesSharedDependenciesOfBatchesOnceOnly() {
        Task a = createTask("a");
        Task b = createTask("b");
        Task c = createTask("c", a, b);
        Task d = createTask("d");
        Task e = createTask("e", b, d);

        taskExecuter.addTasks(toList(c));
        taskExecuter.addTasks(toList(e));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(a, b, c, d, e)));
    }
    
    @Test
    public void testAddTasksAddsDependencies() {
        Task a = createTask("a");
        Task b = createTask("b", a);
        Task c = createTask("c", b, a);
        Task d = createTask("d", c);
        taskExecuter.addTasks(toList(d));

        assertTrue(taskExecuter.hasTask(":a"));
        assertTrue(taskExecuter.hasTask(a));
        assertTrue(taskExecuter.hasTask(":b"));
        assertTrue(taskExecuter.hasTask(b));
        assertTrue(taskExecuter.hasTask(":c"));
        assertTrue(taskExecuter.hasTask(c));
        assertTrue(taskExecuter.hasTask(":d"));
        assertTrue(taskExecuter.hasTask(d));
        assertThat(taskExecuter.getAllTasks(), equalTo(toList(a, b, c, d)));
    }

    @Test
    public void testGetAllTasksReturnsTasksInExecutionOrder() {
        Task d = createTask("d");
        Task c = createTask("c");
        Task b = createTask("b", d, c);
        Task a = createTask("a", b);
        taskExecuter.addTasks(toList(a));

        assertThat(taskExecuter.getAllTasks(), equalTo(toList(c, d, b, a)));
    }

    @Test
    public void testCannotUseGetterMethodsWhenGraphHasNotBeenCalculated() {
        try {
            taskExecuter.hasTask(":a");
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo(
                    "Task information is not available, as this task execution graph has not been populated."));
        }

        try {
            taskExecuter.hasTask(createTask("a"));
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo(
                    "Task information is not available, as this task execution graph has not been populated."));
        }

        try {
            taskExecuter.getAllTasks();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo(
                    "Task information is not available, as this task execution graph has not been populated."));
        }
    }

    @Test
    public void testDiscardsTasksAfterExecute() {
        Task a = createTask("a");
        Task b = createTask("b", a);

        taskExecuter.addTasks(toList(b));
        taskExecuter.execute();

        assertFalse(taskExecuter.hasTask(":a"));
        assertFalse(taskExecuter.hasTask(a));
        assertTrue(taskExecuter.getAllTasks().isEmpty());
    }

    @Test
    public void testCanExecuteMultipleTimes() {
        Task a = createTask("a");
        Task b = createTask("b", a);
        Task c = createTask("c");

        taskExecuter.addTasks(toList(b));
        taskExecuter.execute();
        assertThat(executedTasks, equalTo(toList(a, b)));

        executedTasks.clear();

        taskExecuter.addTasks(toList(c));

        assertThat(taskExecuter.getAllTasks(), equalTo(toList(c)));

        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(c)));
    }

    @Test
    public void testCannotAddTaskWithCircularReference() {
        Task a = createTask("a");
        Task b = createTask("b", a);
        Task c = createTask("c", b);
        a.dependsOn(c);

        try {
            taskExecuter.addTasks(toList(c));
            fail();
        } catch (CircularReferenceException e) {
            // Expected
        }
    }

    @Test
    public void testNotifiesGraphListenerBeforeExecute() {
        final TaskExecutionGraphListener listener = context.mock(TaskExecutionGraphListener.class);
        Task a = createTask("a");

        taskExecuter.addTaskExecutionGraphListener(listener);
        taskExecuter.addTasks(toList(a));

        context.checking(new Expectations() {{
            one(listener).graphPopulated(taskExecuter);
        }});

        taskExecuter.execute();
    }

    @Test
    public void testExecutesWhenReadyClosureBeforeExecute() {
        final TestClosure runnable = context.mock(TestClosure.class);
        Task a = createTask("a");

        taskExecuter.whenReady(toClosure(runnable));

        taskExecuter.addTasks(toList(a));

        context.checking(new Expectations() {{
            one(runnable).call(taskExecuter);
        }});

        taskExecuter.execute();
    }

    @Test
    public void testNotifiesTaskListenerAsTasksAreExecuted() {
        final TaskExecutionListener listener = context.mock(TaskExecutionListener.class);
        final Task a = createTask("a");
        final Task b = createTask("b");

        taskExecuter.addTaskExecutionListener(listener);
        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations() {{
            one(listener).beforeExecute(a);
            one(listener).afterExecute(a, null);
            one(listener).beforeExecute(b);
            one(listener).afterExecute(b, null);
        }});

        taskExecuter.execute();
    }

    @Test
    public void testNotifiesTaskListenerWhenTaskFails() {
        final TaskExecutionListener listener = context.mock(TaskExecutionListener.class);
        final RuntimeException failure = new RuntimeException();
        final Task a = createTask("a");
        a.doLast(new TaskAction() {
            public void execute(Task task) {
                throw failure;
            }
        });

        taskExecuter.addTaskExecutionListener(listener);
        taskExecuter.addTasks(toList(a));

        context.checking(new Expectations() {{
            one(listener).beforeExecute(a);
            one(listener).afterExecute(with(sameInstance(a)), with(notNullValue(GradleScriptException.class)));
        }});

        try {
            taskExecuter.execute();
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void testNotifiesBeforeTaskClosureAsTasksAreExecuted() {
        final TestClosure runnable = context.mock(TestClosure.class);
        final Task a = createTask("a");
        final Task b = createTask("b");

        taskExecuter.beforeTask(toClosure(runnable));

        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations() {{
            one(runnable).call(a);
            one(runnable).call(b);
        }});

        taskExecuter.execute();
    }
    
    @Test
    public void testNotifiesAfterTaskClosureAsTasksAreExecuted() {
        final TestClosure runnable = context.mock(TestClosure.class);
        final Task a = createTask("a");
        final Task b = createTask("b");

        taskExecuter.afterTask(toClosure(runnable));

        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations() {{
            one(runnable).call(a);
            one(runnable).call(b);
        }});

        taskExecuter.execute();
    }

    private Task createTask(String name, final Task... dependsOn) {
        final TaskInternal task = new DefaultTask(root, name);
        task.dependsOn((Object[]) dependsOn);
        task.doFirst(new TaskAction() {
            public void execute(Task task) {
                executedTasks.add(task);
            }
        });
        return task;
    }

}
