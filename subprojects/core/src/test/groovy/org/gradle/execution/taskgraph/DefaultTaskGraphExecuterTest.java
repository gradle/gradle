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

import org.gradle.api.Action;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.internal.InternalTaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Factories;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.progress.TestBuildOperationExecutor;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.work.DefaultWorkerLeaseService;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.Path;
import org.gradle.util.TestClosure;
import org.gradle.util.TestUtil;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.TestUtil.toClosure;
import static org.gradle.util.WrapUtil.toList;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultTaskGraphExecuterTest {
    final JUnit4Mockery context = new JUnit4GroovyMockery();
    final ListenerManager listenerManager = context.mock(ListenerManager.class);
    final ResourceLockCoordinationService resourceLockCoordinationService = new DefaultResourceLockCoordinationService();
    final BuildCancellationToken cancellationToken = context.mock(BuildCancellationToken.class);
    final BuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor();
    final WorkerLeaseService workerLeases = new DefaultWorkerLeaseService(resourceLockCoordinationService, true, 1);
    private WorkerLeaseRegistry.WorkerLease parentWorkerLease;
    final TaskExecuter executer = context.mock(TaskExecuter.class);
    final ExecutorFactory executorFactory = context.mock(ExecutorFactory.class);
    DefaultTaskGraphExecuter taskExecuter;
    ProjectInternal root;
    List<Task> executedTasks = new ArrayList<Task>();

    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider();

    @Before
    public void setUp() {
        root = TestUtil.create(temporaryFolder).rootProject();
        final InternalTaskExecutionListener taskExecutionListener = context.mock(InternalTaskExecutionListener.class);
        context.checking(new Expectations(){{
            one(listenerManager).createAnonymousBroadcaster(TaskExecutionGraphListener.class);
            will(returnValue(new ListenerBroadcast<TaskExecutionGraphListener>(TaskExecutionGraphListener.class)));
            one(listenerManager).createAnonymousBroadcaster(TaskExecutionListener.class);
            will(returnValue(new ListenerBroadcast<TaskExecutionListener>(TaskExecutionListener.class)));
            allowing(cancellationToken).isCancellationRequested();
            one(listenerManager).getBroadcaster(InternalTaskExecutionListener.class);
            will(returnValue(taskExecutionListener));
            ignoring(taskExecutionListener);
            allowing(listenerManager);
            allowing(executorFactory);
        }});

        parentWorkerLease = workerLeases.getWorkerLease();
        resourceLockCoordinationService.withStateLock(DefaultResourceLockCoordinationService.lock(parentWorkerLease));
        taskExecuter = new DefaultTaskGraphExecuter(listenerManager, new DefaultTaskPlanExecutor(1, executorFactory, workerLeases), Factories.constant(executer), cancellationToken, buildOperationExecutor, workerLeases, resourceLockCoordinationService);
    }

    @After
    public void tearDown() {
        resourceLockCoordinationService.withStateLock(DefaultResourceLockCoordinationService.unlock(parentWorkerLease));
        workerLeases.stop();
    }

    @Test
    public void testExecutesTasksInDependencyOrder() {
        Task a = task("a");
        Task b = task("b", a);
        Task c = task("c", b, a);
        Task d = task("d", c);

        taskExecuter.addTasks(toList(d));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(a, b, c, d)));
    }

    @Test
    public void testExecutesDependenciesInNameOrder() {
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");
        Task d = task("d", b, a, c);

        taskExecuter.addTasks(toList(d));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(a, b, c, d)));
    }

    @Test
    public void testExecutesTasksInASingleBatchInNameOrder() {
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");

        taskExecuter.addTasks(toList(b, c, a));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(a, b, c)));
    }

    @Test
    public void testExecutesBatchesInOrderAdded() {
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");
        Task d = task("d");

        taskExecuter.addTasks(toList(c, b));
        taskExecuter.addTasks(toList(d, a));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(b, c, a, d)));
    }

    @Test
    public void testExecutesSharedDependenciesOfBatchesOnceOnly() {
        Task a = task("a");
        Task b = task("b");
        Task c = task("c", a, b);
        Task d = task("d");
        Task e = task("e", b, d);

        taskExecuter.addTasks(toList(c));
        taskExecuter.addTasks(toList(e));
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(a, b, c, d, e)));
    }

    @Test
    public void testAddTasksAddsDependencies() {
        Task a = task("a");
        Task b = task("b", a);
        Task c = task("c", b, a);
        Task d = task("d", c);
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
        Task d = task("d");
        Task c = task("c");
        Task b = task("b", d, c);
        Task a = task("a", b);
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
            taskExecuter.hasTask(task("a"));
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
        Task a = task("a");
        Task b = task("b", a);

        taskExecuter.addTasks(toList(b));
        taskExecuter.execute();

        assertFalse(taskExecuter.hasTask(":a"));
        assertFalse(taskExecuter.hasTask(a));
        assertTrue(taskExecuter.getAllTasks().isEmpty());
    }

    @Test
    public void testCanExecuteMultipleTimes() {
        Task a = task("a");
        Task b = task("b", a);
        Task c = task("c");

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
        Task b = task("b", a);
        Task c = task("c", b);
        dependsOn(a, c);

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
        Task a = task("a");

        taskExecuter.addTaskExecutionGraphListener(listener);
        taskExecuter.addTasks(toList(a));

        context.checking(new Expectations() {{
            one(listener).graphPopulated(taskExecuter);
        }});

        taskExecuter.execute();
    }

    @Test
    public void testExecutesWhenReadyClosureBeforeExecute() {
        assertExecutesWhenReadyListenerBeforeExecute(new Action<TestClosure>() {
            @Override
            public void execute(TestClosure testClosure) {
                taskExecuter.whenReady(toClosure(testClosure));
            }
        });
    }

    @Test
    public void testExecutesWhenReadyActionBeforeExecute() {
        assertExecutesWhenReadyListenerBeforeExecute(new Action<TestClosure>() {
            @Override
            public void execute(TestClosure testClosure) {
                taskExecuter.whenReady(toAction(testClosure));
            }
        });
    }

    void assertExecutesWhenReadyListenerBeforeExecute(Action<TestClosure> whenReadySubscriber) {
        final TestClosure runnable = context.mock(TestClosure.class);
        Task a = task("a");

        whenReadySubscriber.execute(runnable);

        taskExecuter.addTasks(toList(a));

        context.checking(new Expectations() {{
            one(runnable).call(taskExecuter);
        }});

        taskExecuter.execute();
    }

    @Test
    public void testStopsExecutionOnFirstFailureWhenNoFailureHandlerProvided() {
        final RuntimeException failure = new RuntimeException();
        final Task a = brokenTask("a", failure);
        final Task b = task("b");

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
        final Task a = brokenTask("a", failure);
        final Task b = task("b");

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
        assertNotifiesBeforeTaskListenerAsTasksAreExecuted(new Action<TestClosure>() {
            @Override
            public void execute(TestClosure testClosure) {
                taskExecuter.beforeTask(toClosure(testClosure));
            }
        });
    }

    @Test
    public void testNotifiesBeforeTaskActionAsTasksAreExecuted() {
        assertNotifiesBeforeTaskListenerAsTasksAreExecuted(new Action<TestClosure>() {
            @Override
            public void execute(TestClosure testClosure) {
                taskExecuter.beforeTask(toAction(testClosure));
            }
        });
    }

    void assertNotifiesBeforeTaskListenerAsTasksAreExecuted(Action<TestClosure> beforeTaskSubscriber) {
        final TestClosure runnable = context.mock(TestClosure.class);
        beforeTaskSubscriber.execute(runnable);

        final Task a = task("a");
        final Task b = task("b");
        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations() {{
            one(runnable).call(a);
            one(runnable).call(b);
        }});

        taskExecuter.execute();
    }

    @Test
    public void testNotifiesAfterTaskClosureAsTasksAreExecuted() {
        assertNotifiesAfterTaskListenerAsTasksAreExecuted(new Action<TestClosure>() {
            @Override
            public void execute(TestClosure testClosure) {
                taskExecuter.afterTask(toClosure(testClosure));
            }
        });
    }

    @Test
    public void testNotifiesAfterTaskActionAsTasksAreExecuted() {
        assertNotifiesAfterTaskListenerAsTasksAreExecuted(new Action<TestClosure>() {
            @Override
            public void execute(TestClosure testClosure) {
                taskExecuter.afterTask(toAction(testClosure));
            }
        });
    }

    void assertNotifiesAfterTaskListenerAsTasksAreExecuted(Action<TestClosure> afterTaskSubscriber) {
        final TestClosure runnable = context.mock(TestClosure.class);
        afterTaskSubscriber.execute(runnable);

        final Task a = task("a");
        final Task b = task("b");
        taskExecuter.addTasks(toList(a, b));

        context.checking(new Expectations() {{
            one(runnable).call(a);
            one(runnable).call(b);
        }});

        taskExecuter.execute();
    }

    @Test
    public void doesNotExecuteFilteredTasks() {
        final Task a = task("a", task("a-dep"));
        Task b = task("b");
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
        final Task a = task("a", task("a-dep"));
        Task b = task("b");
        Task c = task("c", a, b);
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
        final Task a = brokenTask("a", failure);
        final Task b = task("b");
        final Task c = task("c", b);

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

    static Action toAction(final TestClosure closure) {
        return new Action() {
            @Override
            public void execute(Object o) {
                closure.call(o);
            }
        };
    }

    private void dependsOn(final Task task, final Task... dependsOn) {
        context.checking(new Expectations() {{
            TaskDependency taskDependency = context.mock(TaskDependency.class);
            allowing(task).getTaskDependencies();
            will(returnValue(taskDependency));
            allowing(taskDependency).getDependencies(task);
            will(returnValue(toSet(dependsOn)));
        }});
    }

    private Task brokenTask(String name, final RuntimeException failure, final Task... dependsOn) {
        final TaskInternal task = context.mock(TaskInternal.class);
        final TaskStateInternal state = context.mock(TaskStateInternal.class);
        final TaskOutputs outputs = context.mock(DefaultTaskOutputs.class);
        setExpectations(name, task, state, outputs);
        dependsOn(task, dependsOn);
        context.checking(new Expectations() {{
            atMost(1).of(executer).execute(with(sameInstance(task)), with(sameInstance(state)), with(notNullValue(TaskExecutionContext.class)));
            will(new ExecuteTaskAction(task));
            allowing(state).getFailure();
            will(returnValue(failure));
            allowing(state).rethrowFailure();
            will(throwException(failure));
        }});
        return task;
    }

    private Task task(final String name, final Task... dependsOn) {
        final TaskInternal task = context.mock(TaskInternal.class);
        final TaskStateInternal state = context.mock(TaskStateInternal.class);
        final TaskOutputs outputs = context.mock(DefaultTaskOutputs.class);
        setExpectations(name, task, state, outputs);
        dependsOn(task, dependsOn);
        context.checking(new Expectations() {{
            atMost(1).of(executer).execute(with(sameInstance(task)), with(sameInstance(state)), with(notNullValue(TaskExecutionContext.class)));
            will(new ExecuteTaskAction(task));
            allowing(state).getFailure();
            will(returnValue(null));
        }});
        return task;
    }

    private TaskInternal createTask(final String name) {
        TaskInternal task = context.mock(TaskInternal.class);
        TaskStateInternal state = context.mock(TaskStateInternal.class);
        final TaskOutputs outputs = context.mock(DefaultTaskOutputs.class);
        setExpectations(name, task, state, outputs);
        return task;
    }

    private void setExpectations(final String name, final TaskInternal task, final TaskStateInternal state, final TaskOutputs outputs) {
        context.checking(new Expectations() {{
            allowing(task).getProject();
            will(returnValue(root));
            allowing(task).getName();
            will(returnValue(name));
            allowing(task).getPath();
            will(returnValue(":" + name));
            allowing(task).getIdentityPath();
            will(returnValue(Path.path(":" + name)));
            allowing(task).getState();
            will(returnValue(state));
            allowing((Task) task).getState();
            will(returnValue(state));
            allowing(task).getMustRunAfter();
            will(returnValue(new DefaultTaskDependency()));
            allowing(task).getFinalizedBy();
            will(returnValue(new DefaultTaskDependency()));
            allowing(task).getShouldRunAfter();
            will(returnValue(new DefaultTaskDependency()));
            allowing(task).compareTo(with(notNullValue(TaskInternal.class)));
            will(new org.jmock.api.Action() {
                public Object invoke(Invocation invocation) throws Throwable {
                    return name.compareTo(((Task) invocation.getParameter(0)).getName());
                }

                public void describeTo(Description description) {
                    description.appendText("compare to");
                }
            });
            allowing(task).getOutputs();
            will(returnValue(outputs));
            allowing(outputs).getFiles();
            will(returnValue(root.files()));
        }});
    }

    private class ExecuteTaskAction implements org.jmock.api.Action {
        private final TaskInternal task;

        public ExecuteTaskAction(TaskInternal task) {
            this.task = task;
        }

        public Object invoke(Invocation invocation) throws Throwable {
            executedTasks.add(task);
            return null;
        }

        public void describeTo(Description description) {
            description.appendText("execute task");
        }
    }

}
