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


import org.gradle.api.CircularReferenceException
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactStateCacheAccess
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskState
import org.gradle.cache.PersistentIndexedCache
import org.gradle.execution.TaskFailureHandler
import org.gradle.internal.Factory
import org.gradle.messaging.serialize.Serializer
import org.hamcrest.Description
import org.jmock.api.Invocation
import spock.lang.Specification

import static org.gradle.util.HelperUtil.createRootProject
import static org.gradle.util.WrapUtil.toList
import static org.gradle.util.WrapUtil.toSet

public class TaskExecutionPlanTest extends Specification {

    TaskExecutionPlan executionPlan
    ProjectInternal root;
    Spec<TaskInfo> anyTask = Specs.satisfyAll();

    def setup() {
        root = createRootProject();
        executionPlan = new TaskExecutionPlan()
    }

    def "returns tasks in dependency order"() {
        given:
        Task a = task("a");
        Task b = task("b", a);
        Task c = task("c", b, a);
        Task d = task("d", c);

        when:
        executionPlan.addToTaskGraph(toList(d))

        then:
        executedTasks == [a, b, c, d]
    }

    def "returns task dependencies in name order"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");
        Task d = task("d", b, a, c);

        when:
        executionPlan.addToTaskGraph(toList(d));

        then:
        executedTasks == [a, b, c, d]
    }

    def "returns a single batch of tasks in name order"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");

        when:
        executionPlan.addToTaskGraph(toList(b, c, a));

        then:
        executedTasks == [a, b, c]
    }

    def "returns separately added tasks in order added"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");
        Task d = task("d");

        when:
        executionPlan.addToTaskGraph(toList(c, b));
        executionPlan.addToTaskGraph(toList(d, a));

        then:
        executedTasks == [b, c, a, d];
    }

    def "common tasks in separate batches are returned only once"() {
        Task a = task("a");
        Task b = task("b");
        Task c = task("c", a, b);
        Task d = task("d");
        Task e = task("e", b, d);

        when:
        executionPlan.addToTaskGraph(toList(c));
        executionPlan.addToTaskGraph(toList(e));

        then:
        executedTasks == [a, b, c, d, e];
    }

    def "all dependencies added when adding tasks"() {
        Task a = task("a");
        Task b = task("b", a);
        Task c = task("c", b, a);
        Task d = task("d", c);

        when:
        executionPlan.addToTaskGraph(toList(d));

        then:
        executionPlan.hasTask(a)
        executionPlan.hasTask(b)
        executionPlan.hasTask(c)
        executionPlan.hasTask(d)
        executionPlan.getTasks() == [a, b, c, d];
        executedTasks == [a, b, c, d]
    }

    def "getAllTasks returns tasks in execution order"() {
        Task d = task("d");
        Task c = task("c");
        Task b = task("b", d, c);
        Task a = task("a", b);

        when:
        executionPlan.addToTaskGraph(toList(a));

        then:
        executionPlan.getTasks() == [c, d, b, a]
        executedTasks == [c, d, b, a]
    }

    def "cannot add task with circular reference"() {
        Task a = createTask("a");
        Task b = task("b", a);
        Task c = task("c", b);
        dependsOn(a, c);

        when:
        executionPlan.addToTaskGraph([c])

        then:
        thrown CircularReferenceException
    }

    def "stops returning tasks on first failure when no failure handler provided"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = brokenTask("a", failure);
        Task b = task("b");

        when:
        executionPlan.addToTaskGraph([a, b])
        def taskInfo = executionPlan.getTaskToExecute(anyTask)
        executionPlan.taskFailed(taskInfo)

        then:
        executedTasks == []

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "stops execution on failure when failure handler indicates that execution should stop"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = brokenTask("a", failure);
        Task b = task("b");

        TaskFailureHandler handler = Mock()
        RuntimeException wrappedFailure = new RuntimeException("wrapped");
        handler.onTaskFailure(a) >> {
            throw wrappedFailure
        }

        when:
        executionPlan.useFailureHandler(handler);
        executionPlan.addToTaskGraph([a, b])
        final def taskInfo = executionPlan.getTaskToExecute(anyTask)
        executionPlan.taskFailed(taskInfo)

        then:
        executedTasks == []

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == wrappedFailure
    }

    def "continues to return tasks when failure handler indicates that execution should continue"() {
        RuntimeException failure = new RuntimeException();
        Task a = brokenTask("a", failure);
        Task b = task("b");

        TaskFailureHandler handler = Mock()
        RuntimeException wrappedFailure = new RuntimeException("wrapped");
        handler.onTaskFailure(a) >> {
            throw wrappedFailure
        }

        when:
        executionPlan.useFailureHandler(handler);
        executionPlan.addToTaskGraph([a, b])
        final def taskInfo = executionPlan.getTaskToExecute(anyTask)
        executionPlan.taskFailed(taskInfo)

        then:
        executedTasks == []

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == wrappedFailure
    }

    def "does not attempt to execute tasks whose dependencies failed to execute"() {
        RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b", a)
        final Task c = task("c")

        TaskFailureHandler handler = Mock()
        handler.onTaskFailure(a) >> {
            // Ignore failure
        }

        when:
        executionPlan.useFailureHandler(handler)
        executionPlan.addToTaskGraph([b, c])
        final def taskInfo = executionPlan.getTaskToExecute(anyTask)
        executionPlan.taskFailed(taskInfo)

        then:
        executedTasks == [c]

        when:
        executionPlan.awaitCompletion()

        then:
        notThrown(RuntimeException)
    }

    def getExecutedTasks() {
        def tasks = []
        def taskInfo
        while ((taskInfo = executionPlan.getTaskToExecute(anyTask)) != null) {
            tasks << taskInfo.task
            executionPlan.taskComplete(taskInfo)
        }
        return tasks
    }

    /*

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
        final TestClosure runnable = context.mock(TestClosure.class);
        Task a = task("a");

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
        final Task a = task("a");
        final Task b = task("b");

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
        final Task a = brokenTask("a", failure);

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
    public void testNotifiesBeforeTaskClosureAsTasksAreExecuted() {
        final TestClosure runnable = context.mock(TestClosure.class);
        final Task a = task("a");
        final Task b = task("b");

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
        final Task a = task("a");
        final Task b = task("b");

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
        taskExecuter.execute();

        assertThat(executedTasks, equalTo(toList(a, c)));
    }
*/

    private void dependsOn(TaskInternal task, final Task... dependsOnTasks) {
        TaskDependency taskDependency = Mock()
        task.getTaskDependencies() >> taskDependency
        taskDependency.getDependencies(task) >> toSet(dependsOnTasks)
    }
    
    private Task brokenTask(String name, final RuntimeException failure, final Task... dependsOnTasks) {
        final TaskInternal task = createTask(name);
        dependsOn(task, dependsOnTasks);

        task.state.getFailure() >> failure
        task.state.rethrowFailure() >> { throw failure }
        return task;
    }
    
    private TaskInternal task(final String name, final Task... dependsOnTasks) {
        def task = createTask(name);
        dependsOn(task, dependsOnTasks);
        task.state.getFailure() >> null
        return task;
    }
    
    private TaskInternal createTask(final String name) {
        TaskInternal task = Mock()
        TaskState state = Mock()
        task.getProject() >> root
        task.name >> name
        task.path >> ':' + name
        task.state >> state
        task.toString() >> "task $name"
        task.compareTo(_ as TaskInternal) >> { TaskInternal taskInternal ->
            return name.compareTo(taskInternal.getName());
        }
        return task;
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

    private static class DirectCacheAccess implements TaskArtifactStateCacheAccess {
        public void useCache(String operationDisplayName, Runnable action) {
            action.run();
        }

        public void longRunningOperation(String operationDisplayName, Runnable action) {
            action.run();
        }

        public <K, V> PersistentIndexedCache createCache(String cacheName, Class<K> keyType, Class<V> valueType) {
            throw new UnsupportedOperationException();
        }

        public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
            throw new UnsupportedOperationException();
        }

        public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType, Serializer<V> valueSerializer) {
            throw new UnsupportedOperationException();
        }
    }
}

