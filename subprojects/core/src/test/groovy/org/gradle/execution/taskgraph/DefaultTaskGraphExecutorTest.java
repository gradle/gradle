/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.taskgraph;

import groovy.lang.Closure;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskState;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.TestClosure;
import org.jmock.Expectations;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.action.CustomAction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.TestUtil.createRootProject;
import static org.gradle.util.TestUtil.toClosure;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultTaskGraphExecutorTest {
    final JUnit4Mockery context = new JUnit4GroovyMockery();
    final ListenerManager listenerManager = context.mock(ListenerManager.class);
    final TaskArtifactStateCacheAccess taskArtifactStateCacheAccess = context.mock(TaskArtifactStateCacheAccess.class);
    DefaultTaskGraphExecuter taskExecuter;
    ProjectInternal root;
    List<Task> executedTasks = new ArrayList<Task>();
    ExecutorTestHelper helper;

    @Before
    public void setUp() {
        root = createRootProject();
        helper = new ExecutorTestHelper(context, root, executedTasks);
        context.checking(new Expectations(){{
            one(listenerManager).createAnonymousBroadcaster(TaskExecutionGraphListener.class);
            will(returnValue(new ListenerBroadcast<TaskExecutionGraphListener>(TaskExecutionGraphListener.class)));
            one(listenerManager).createAnonymousBroadcaster(TaskExecutionListener.class);
            will(returnValue(new ListenerBroadcast<TaskExecutionListener>(TaskExecutionListener.class)));
            allowing(taskArtifactStateCacheAccess).longRunningOperation(with(notNullValue(String.class)), with(notNullValue(Runnable.class)));
            will(new CustomAction("run action") {
                public Object invoke(Invocation invocation) throws Throwable {
                    Runnable action = (Runnable) invocation.getParameter(1);
                    action.run();
                    return null;
                }
            });
        }});
        taskExecuter = new DefaultTaskGraphExecuter(listenerManager, new DefaultTaskPlanExecutor(taskArtifactStateCacheAccess));
    }

    @Test
    public void testExecute() {
        Task a = helper.task("a");

        taskExecuter.addTasks(toList(a));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(a)));
    }

    @Test
    public void testAddTasksAddsDependencies() {
        Task a = helper.task("a");
        Task b = helper.task("b", a);
        Task c = helper.task("c", b, a);
        Task d = helper.task("d", c);
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
        Task d = helper.task("d");
        Task c = helper.task("c");
        Task b = helper.task("b", d, c);
        Task a = helper.task("a", b);
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
            taskExecuter.hasTask(helper.task("a"));
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
        Task a = helper.task("a");
        Task b = helper.task("b", a);

        taskExecuter.addTasks(toList(b));
        taskExecuter.execute();

        assertFalse(taskExecuter.hasTask(":a"));
        assertFalse(taskExecuter.hasTask(a));
        assertTrue(taskExecuter.getAllTasks().isEmpty());
    }

    @Test
    public void testCanExecuteMultipleTimes() {
        Task a = helper.task("a");
        Task b = helper.task("b", a);
        Task c = helper.task("c");

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
        Task a = helper.createTask("a");
        Task b = helper.task("b", a);
        Task c = helper.task("c", b);
        helper.dependsOn(a, c);

        taskExecuter.addTasks(toList(c));
        try {
            taskExecuter.execute();
            fail();
        } catch (CircularReferenceException e) {
            // Expected
        }
    }

    @Test
    public void testNotifiesGraphListenerBeforeExecute() {
        final TaskExecutionGraphListener listener = context.mock(TaskExecutionGraphListener.class);
        Task a = helper.task("a");

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
        Task a = helper.task("a");

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
        final Task a = helper.task("a");
        final Task b = helper.task("b");

        taskExecuter.addTaskExecutionListener(listener);
        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations() {{
            one(listener).beforeExecute(a);
            one(listener).afterExecute(with(equalTo(a)), with(notNullValue(TaskState.class)));
            one(listener).beforeExecute(b);
            one(listener).afterExecute(with(equalTo(b)), with(notNullValue(TaskState.class)));
        }});

        taskExecuter.execute();
    }

    @Test
    public void testNotifiesTaskListenerWhenTaskFails() {
        final TaskExecutionListener listener = context.mock(TaskExecutionListener.class);
        final RuntimeException failure = new RuntimeException();
        final Task a = helper.brokenTask("a", failure);

        taskExecuter.addTaskExecutionListener(listener);
        taskExecuter.addTasks(toList(a));

        context.checking(new Expectations() {{
            one(listener).beforeExecute(a);
            one(listener).afterExecute(with(sameInstance(a)), with(notNullValue(TaskState.class)));
        }});

        try {
            taskExecuter.execute();
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
        
        assertThat(executedTasks, equalTo(toList(a)));
    }

    @Test
    public void testStopsExecutionOnFirstFailureWhenNoFailureHandlerProvided() {
        final RuntimeException failure = new RuntimeException();
        final Task a = helper.brokenTask("a", failure);
        final Task b = helper.task("b");

        taskExecuter.addTasks(toList(a, b));

        try {
            taskExecuter.execute();
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }

        assertThat(executedTasks, equalTo(toList(a)));
    }
    
    @Test
    public void testStopsExecutionOnFailureWhenFailureHandlerIndicatesThatExecutionShouldStop() {
        final TaskFailureHandler handler = context.mock(TaskFailureHandler.class);

        final RuntimeException failure = new RuntimeException();
        final RuntimeException wrappedFailure = new RuntimeException();
        final Task a = helper.brokenTask("a", failure);
        final Task b = helper.task("b");

        taskExecuter.useFailureHandler(handler);
        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations(){{
            one(handler).onTaskFailure(a);
            will(throwException(wrappedFailure));
        }});
        try {
            taskExecuter.execute();
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(wrappedFailure));
        }

        assertThat(executedTasks, equalTo(toList(a)));
    }

    @Test
    public void testNotifiesBeforeTaskClosureAsTasksAreExecuted() {
        final TestClosure runnable = context.mock(TestClosure.class);
        final Task a = helper.task("a");
        final Task b = helper.task("b");

        final Closure closure = toClosure(runnable);
        taskExecuter.beforeTask(closure);

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
        final Task a = helper.task("a");
        final Task b = helper.task("b");

        taskExecuter.afterTask(toClosure(runnable));

        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations() {{
            one(runnable).call(a);
            one(runnable).call(b);
        }});

        taskExecuter.execute();
    }

    @Test
    public void doesNotExecuteFilteredTasks() {
        final Task a = helper.task("a", helper.task("a-dep"));
        Task b = helper.task("b");
        Spec<Task> spec = new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element != a;
            }
        };

        taskExecuter.useFilter(spec);
        taskExecuter.addTasks(toList(a, b));
        assertThat(taskExecuter.getAllTasks(), equalTo(toList(b)));

        taskExecuter.execute();
        
        assertThat(executedTasks, equalTo(toList(b)));
    }

    @Test
    public void doesNotExecuteFilteredDependencies() {
        final Task a = helper.task("a", helper.task("a-dep"));
        Task b = helper.task("b");
        Task c = helper.task("c", a, b);
        Spec<Task> spec = new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element != a;
            }
        };

        taskExecuter.useFilter(spec);
        taskExecuter.addTasks(toList(c));
        assertThat(taskExecuter.getAllTasks(), equalTo(toList(b, c)));
        
        taskExecuter.execute();
                
        assertThat(executedTasks, equalTo(toList(b, c)));
    }

    @Test
    public void willExecuteATaskWhoseDependenciesHaveBeenFilteredOnFailure() {
        final TaskFailureHandler handler = context.mock(TaskFailureHandler.class);
        final RuntimeException failure = new RuntimeException();
        final Task a = helper.brokenTask("a", failure);
        final Task b = helper.task("b");
        final Task c = helper.task("c", b);

        taskExecuter.useFailureHandler(handler);
        taskExecuter.useFilter(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element != b;
            }
        });
        taskExecuter.addTasks(toList(a, c));

        context.checking(new Expectations() {{
            ignoring(handler);
        }});
        try {
            taskExecuter.execute();
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }

        assertThat(executedTasks, equalTo(toList(a, c)));
    }
}
