/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskState;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.listener.ListenerBroadcast;
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
import java.util.Arrays;
import java.util.List;

import static org.gradle.util.TestUtil.createRootProject;
import static org.gradle.util.TestUtil.toClosure;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultTaskPlanExecutorTest {
    final JUnit4Mockery context = new JUnit4GroovyMockery();
    final TaskArtifactStateCacheAccess taskArtifactStateCacheAccess = context.mock(TaskArtifactStateCacheAccess.class);
    DefaultTaskExecutionPlan taskExecutionPlan = new DefaultTaskExecutionPlan();
    ListenerBroadcast<TaskExecutionListener> taskListeners = new ListenerBroadcast<TaskExecutionListener>(TaskExecutionListener.class);
    DefaultTaskPlanExecutor taskPlanExecutor;
    ProjectInternal root;
    List<Task> executedTasks = new ArrayList<Task>();
    ExecutorTestHelper helper;

    @Before
    public void setUp() {
        root = createRootProject();
        helper = new ExecutorTestHelper(context, root, executedTasks);
        context.checking(new Expectations(){{
            allowing(taskArtifactStateCacheAccess).longRunningOperation(with(notNullValue(String.class)), with(notNullValue(Runnable.class)));
            will(new CustomAction("run action") {
                public Object invoke(Invocation invocation) throws Throwable {
                    Runnable action = (Runnable) invocation.getParameter(1);
                    action.run();
                    return null;
                }
            });
        }});
        taskPlanExecutor = new DefaultTaskPlanExecutor(taskArtifactStateCacheAccess);
    }

    @Test
    public void testExecutesTasksInDependencyOrder() {
        Task a = helper.task("a");
        Task b = helper.task("b", a);
        Task c = helper.task("c", b, a);
        Task d = helper.task("d", c);

        addTasksToPlan(d);
        execute();

        assertThat(executedTasks, equalTo(toList(a, b, c, d)));
    }

    private void execute() {
        taskExecutionPlan.determineExecutionPlan();
        process();
    }

    private void process() {
        try {
            taskPlanExecutor.process(taskExecutionPlan, taskListeners.getSource());
        } finally {
            taskExecutionPlan.clear();
        }
    }

    private void addTasksToPlan(Task... tasks) {
        taskExecutionPlan.addToTaskGraph(Arrays.asList(tasks));
    }

    @Test
    public void testExecutesDependenciesInNameOrder() {
        Task a = helper.task("a");
        Task b = helper.task("b");
        Task c = helper.task("c");
        Task d = helper.task("d", b, a, c);

        addTasksToPlan(d);
        execute();

        assertThat(executedTasks, equalTo(toList(a, b, c, d)));
    }

    @Test
    public void testExecutesTasksInASingleBatchInNameOrder() {
        Task a = helper.task("a");
        Task b = helper.task("b");
        Task c = helper.task("c");

        addTasksToPlan(b, c, a);
        execute();

        assertThat(executedTasks, equalTo(toList(a, b, c)));
    }

    @Test
    public void testExecutesBatchesInOrderAdded() {
        Task a = helper.task("a");
        Task b = helper.task("b");
        Task c = helper.task("c");
        Task d = helper.task("d");

        addTasksToPlan(c, b);
        addTasksToPlan(d, a);
        execute();

        assertThat(executedTasks, equalTo(toList(b, c, a, d)));
    }

    @Test
    public void testExecutesSharedDependenciesOfBatchesOnceOnly() {
        Task a = helper.task("a");
        Task b = helper.task("b");
        Task c = helper.task("c", a, b);
        Task d = helper.task("d");
        Task e = helper.task("e", b, d);

        addTasksToPlan(c);
        addTasksToPlan(e);
        execute();

        assertThat(executedTasks, equalTo(toList(a, b, c, d, e)));
    }

    @Test
    public void testDiscardsTasksAfterExecute() {
        Task a = helper.task("a");
        Task b = helper.task("b", a);

        addTasksToPlan(b);
        execute();

        assertTrue(taskExecutionPlan.getTasks().isEmpty());
    }

    @Test
    public void testCanExecuteMultipleTimes() {
        Task a = helper.task("a");
        Task b = helper.task("b", a);
        Task c = helper.task("c");

        addTasksToPlan(b);
        execute();
        assertThat(executedTasks, equalTo(toList(a, b)));

        executedTasks.clear();

        addTasksToPlan(c);

        taskExecutionPlan.determineExecutionPlan();
        assertThat(taskExecutionPlan.getTasks(), equalTo(toList(c)));

        process();
        assertThat(executedTasks, equalTo(toList(c)));
    }

    @Test
    public void testCannotAddTaskWithCircularReference() {
        Task a = helper.createTask("a");
        Task b = helper.task("b", a);
        Task c = helper.task("c", b);
        helper.dependsOn(a, c);

        addTasksToPlan(c);
        try {
            execute();
            fail();
        } catch (CircularReferenceException e) {
            // Expected
        }
    }

    @Test
    public void testNotifiesTaskListenerAsTasksAreExecuted() {
        final TaskExecutionListener listener = context.mock(TaskExecutionListener.class);
        final Task a = helper.task("a");
        final Task b = helper.task("b");

        taskListeners.add(listener);
        addTasksToPlan(a, b);

        context.checking(new Expectations() {{
            one(listener).beforeExecute(a);
            one(listener).afterExecute(with(equalTo(a)), with(notNullValue(TaskState.class)));
            one(listener).beforeExecute(b);
            one(listener).afterExecute(with(equalTo(b)), with(notNullValue(TaskState.class)));
        }});

        execute();
    }

    @Test
    public void testNotifiesTaskListenerWhenTaskFails() {
        final TaskExecutionListener listener = context.mock(TaskExecutionListener.class);
        final RuntimeException failure = new RuntimeException();
        final Task a = helper.brokenTask("a", failure);

        taskListeners.add(listener);
        addTasksToPlan(a);

        context.checking(new Expectations() {{
            one(listener).beforeExecute(a);
            one(listener).afterExecute(with(sameInstance(a)), with(notNullValue(TaskState.class)));
        }});

        try {
            execute();
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

        addTasksToPlan(a, b);

        try {
            execute();
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

        taskExecutionPlan.useFailureHandler(handler);
        addTasksToPlan(a, b);

        context.checking(new Expectations(){{
            one(handler).onTaskFailure(a);
            will(throwException(wrappedFailure));
        }});
        try {
            execute();
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
        taskListeners.add(new ClosureBackedMethodInvocationDispatch("beforeExecute", closure));

        addTasksToPlan(a, b);

        context.checking(new Expectations() {{
            one(runnable).call(a);
            one(runnable).call(b);
        }});

        execute();
    }

    @Test
    public void testNotifiesAfterTaskClosureAsTasksAreExecuted() {
        final TestClosure runnable = context.mock(TestClosure.class);
        final Task a = helper.task("a");
        final Task b = helper.task("b");

        taskListeners.add(new ClosureBackedMethodInvocationDispatch("afterExecute", toClosure(runnable)));

        addTasksToPlan(a, b);

        context.checking(new Expectations() {{
            one(runnable).call(a);
            one(runnable).call(b);
        }});

        execute();
    }
}
