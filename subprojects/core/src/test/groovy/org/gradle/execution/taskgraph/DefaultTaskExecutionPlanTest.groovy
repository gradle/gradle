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

public class DefaultTaskExecutionPlanTest extends Specification {

    DefaultTaskExecutionPlan executionPlan
    ProjectInternal root;
    Spec<TaskInfo> anyTask = Specs.satisfyAll();

    def setup() {
        root = createRootProject();
        executionPlan = new DefaultTaskExecutionPlan()
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

    def "stops returning tasks on task execution failure"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = task("a");
        Task b = task("b");
        executionPlan.addToTaskGraph([a, b])

        when:
        def taskInfoA = taskToExecute
        taskInfoA.executionFailure = failure
        executionPlan.taskComplete(taskInfoA)

        then:
        executedTasks == []

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    protected TaskInfo getTaskToExecute() {
        executionPlan.getTaskToExecute(anyTask)
    }

    def "stops returning tasks on first task failure when no failure handler provided"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = brokenTask("a", failure);
        Task b = task("b");

        when:
        executionPlan.addToTaskGraph([a, b])

        then:
        executedTasks == [a]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "stops execution on task failure when failure handler indicates that execution should stop"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = brokenTask("a", failure);
        Task b = task("b");

        executionPlan.addToTaskGraph([a, b])

        TaskFailureHandler handler = Mock()
        RuntimeException wrappedFailure = new RuntimeException("wrapped");
        handler.onTaskFailure(a) >> {
            throw wrappedFailure
        }

        when:
        executionPlan.useFailureHandler(handler);

        then:
        executedTasks == [a]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == wrappedFailure
    }

    def "continues to return tasks and rethrows failure on completion when failure handler indicates that execution should continue"() {
        RuntimeException failure = new RuntimeException();
        Task a = brokenTask("a", failure);
        Task b = task("b");
        executionPlan.addToTaskGraph([a, b])

        TaskFailureHandler handler = Mock()
        handler.onTaskFailure(a) >> {
        }

        when:
        executionPlan.useFailureHandler(handler);

        then:
        executedTasks == [a, b]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "does not attempt to execute tasks whose dependencies failed to execute"() {
        RuntimeException failure = new RuntimeException()
        final Task a = brokenTask("a", failure)
        final Task b = task("b", a)
        final Task c = task("c")
        executionPlan.addToTaskGraph([b, c])

        TaskFailureHandler handler = Mock()
        handler.onTaskFailure(a) >> {
            // Ignore failure
        }

        when:
        executionPlan.useFailureHandler(handler)

        then:
        executedTasks == [a, c]

        when:
        executionPlan.awaitCompletion()

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "clear removes all tasks"() {
        given:
        Task a = task("a");

        when:
        executionPlan.addToTaskGraph(toList(a));
        executionPlan.clear()

        then:
        executionPlan.getTasks() == []
        executedTasks == []
    }

    def getExecutedTasks() {
        def tasks = []
        def taskInfo
        while ((taskInfo = taskToExecute) != null) {
            tasks << taskInfo.task
            executionPlan.taskComplete(taskInfo)
        }
        return tasks
    }

    def "can add additional tasks after execution and clear"() {
        given:
        Task a = task("a")
        Task b = task("b")

        when:
        executionPlan.addToTaskGraph([a])

        then:
        executedTasks == [a]

        when:
        executionPlan.clear()
        executionPlan.addToTaskGraph([b])

        then:
        executedTasks == [b]
    }

    def "does not execute filtered tasks"() {
        given:
        Task a = task("a", task("a-dep"))
        Task b = task("b")

        when:
        executionPlan.useFilter({ it != a } as Spec<Task>);
        executionPlan.addToTaskGraph([a, b])

        then:
        executionPlan.getTasks() == [b]
        executedTasks == [b]
    }

    def "does not execute filtered dependencies"() {
        given:
        Task a = task("a", task("a-dep"))
        Task b = task("b")
        Task c = task("c", a, b)

        when:

        executionPlan.useFilter({ it != a } as Spec<Task>)
        executionPlan.addToTaskGraph([c])

        then:
        executionPlan.tasks == [b, c]
        executedTasks == [b, c]
    }

    def "will execute a task whose dependencies have been filtered"() {
        given:
        Task b = task("b")
        Task c = task("c", b)

        when:
        executionPlan.useFilter({ it != b } as Spec<Task>)
        executionPlan.addToTaskGraph([c]);

        then:
        executedTasks == [c]
    }

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

