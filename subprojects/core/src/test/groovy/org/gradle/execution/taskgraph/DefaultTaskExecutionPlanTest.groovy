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

package org.gradle.execution.taskgraph

import org.gradle.api.CircularReferenceException
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskState
import org.gradle.execution.TaskFailureHandler
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.HelperUtil.createChildProject
import static org.gradle.util.HelperUtil.createRootProject
import static org.gradle.util.WrapUtil.toList

public class DefaultTaskExecutionPlanTest extends Specification {

    @Rule ConcurrentTestUtil concurrent = new ConcurrentTestUtil()
    DefaultTaskExecutionPlan executionPlan
    DefaultProject root;

    def setup() {
        root = createRootProject();
        executionPlan = new DefaultTaskExecutionPlan()
    }

    private void addToGraphAndPopulate(List tasks) {
        executionPlan.addToTaskGraph(tasks)
        executionPlan.determineExecutionPlan()
    }

    def "returns tasks in dependency order"() {
        given:
        Task a = task("a");
        Task b = task("b", dependsOn: [a]);
        Task c = task("c", dependsOn: [b, a]);
        Task d = task("d", dependsOn: [c]);

        when:
        addToGraphAndPopulate([d])

        then:
        executedTasks == [a, b, c, d]
    }

    def "returns task dependencies in name order"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");
        Task d = task("d", dependsOn: [b, a, c]);

        when:
        addToGraphAndPopulate([d])

        then:
        executedTasks == [a, b, c, d]
    }

    def "returns a single batch of tasks in name order"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c");

        when:
        addToGraphAndPopulate(toList(b, c, a));

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
        executionPlan.addToTaskGraph(toList(d, a))
        executionPlan.determineExecutionPlan()

        then:
        executedTasks == [b, c, a, d];
    }

    def "returns must run after task dependencies in name order"() {
        given:
        Task a = task("a");
        Task b = task("b");
        Task c = task("c", mustRunAfter: [b, a]);
        Task d = task("d", dependsOn: [b, a]);

        when:
        addToGraphAndPopulate([c, d]);

        then:
        executedTasks == [a, b, c, d]
    }

    def "common tasks in separate batches are returned only once"() {
        Task a = task("a");
        Task b = task("b");
        Task c = task("c", dependsOn: [a, b]);
        Task d = task("d");
        Task e = task("e", dependsOn: [b, d]);

        when:
        executionPlan.addToTaskGraph(toList(c));
        executionPlan.addToTaskGraph(toList(e));
        executionPlan.determineExecutionPlan();

        then:
        executedTasks == [a, b, c, d, e];
    }

    def "all dependencies added when adding tasks"() {
        Task a = task("a");
        Task b = task("b", dependsOn: [a]);
        Task c = task("c", dependsOn: [b, a]);
        Task d = task("d", dependsOn: [c]);

        when:
        addToGraphAndPopulate(toList(d));

        then:
        executionPlan.getTasks() == [a, b, c, d];
        executedTasks == [a, b, c, d]
    }

    def "must run after ordering is honoured for tasks added separately to graph"() {
        Task a = task("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", mustRunAfter: [b])

        when:
        executionPlan.addToTaskGraph([c])
        executionPlan.addToTaskGraph([b])
        executionPlan.determineExecutionPlan()

        then:
        executedTasks == [a, b, c]
    }

    def "must run after ordering is honoured for dependencies"() {
        Task b = task("b")
        Task a = task("a", mustRunAfter: [b])
        Task c = task("c", dependsOn: [a, b])

        when:
        addToGraphAndPopulate([c])

        then:
        executedTasks == [b, a, c]
    }

    def "mustRunAfter dependencies are scheduled before regular dependencies"() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", dependsOn: [a], mustRunAfter: [b])
        Task d = task("d", dependsOn: [b])

        when:
        addToGraphAndPopulate([c, d])

        then:
        executedTasks == [b, a, c, d]
    }

    def "must run after does not pull in tasks that are not in the graph"() {
        Task a = task("a")
        Task b = task("b", mustRunAfter: [a])

        when:
        addToGraphAndPopulate([b])

        then:
        executedTasks == [b]
    }

    def "finalizer tasks are executed if a finalized task is added to the graph"() {
        Task finalizer = task("a")
        Task finalized = task("b", finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([finalized])

        then:
        executedTasks == [finalized, finalizer]
    }

    def "finalizer tasks are executed after the finalized task"() {
        Task finalizer = task("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])

        when:
        addToGraphAndPopulate([finalizer, finalized])

        then:
        executedTasks == [finalized, finalizer]
    }

    def "finalizer tasks and their dependencies are executed even in case of a task failure"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer1 = task("finalizer1", dependsOn: [finalizerDependency])
        Task finalized1 = task("finalized1", finalizedBy: [finalizer1])
        Task finalizer2 = task("finalizer2")
        Task finalized2 = task("finalized2", finalizedBy: [finalizer2], failure: new RuntimeException("failure"))

        when:
        addToGraphAndPopulate([finalized1, finalized2])

        then:
        executedTasks == [finalized1, finalized2, finalizerDependency, finalizer1, finalizer2]
    }

    def "finalizer task is not added to the graph if it is filtered"() {
        given:
        Task finalizer = filteredTask("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])
        Spec<Task> filter = Mock() {
            isSatisfiedBy(_) >> { Task t -> t != finalizer }
        }

        when:
        executionPlan.useFilter(filter);
        addToGraphAndPopulate([finalized])

        then:
        executionPlan.getTasks() == [finalized]
        executedTasks == [finalized]
    }

    def "finalizer tasks and their dependencies are not executed if finalized task did not do any work"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task finalized = task("finalized", finalizedBy: [finalizer], didWork: false)

        when:
        addToGraphAndPopulate([finalized])

        then:
        executedTasks == [finalized]
    }

    def "finalizer tasks and their dependencies are executed if they are previously required even if the finalized task did not do any work"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task finalized = task("finalized", finalizedBy: [finalizer], didWork: false)

        when:
        addToGraphAndPopulate([finalizer, finalized])

        then:
        executedTasks == [finalized, finalizerDependency, finalizer]
    }

    def "finalizer tasks and their dependencies are executed if they are later required via dependency even if the finalized task did not do any work"() {
        Task finalizerDependency = task("finalizerDependency")
        Task finalizer = task("finalizer", dependsOn: [finalizerDependency])
        Task dependsOnFinalizer = task("dependsOnFinalizer", dependsOn: [finalizer])
        Task finalized = task("finalized", finalizedBy: [finalizer], didWork: false)

        when:
        executionPlan.addToTaskGraph([finalized])
        executionPlan.addToTaskGraph([dependsOnFinalizer])
        executionPlan.determineExecutionPlan()

        then:
        executedTasks == [finalized, finalizerDependency, finalizer, dependsOnFinalizer]
    }

    def "finalizer tasks run as soon as possible for tasks that depend on finalized tasks"() {
        Task finalizer = task("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])
        Task dependsOnFinalized = task("dependsOnFinalized", dependsOn: [finalized])

        when:
        addToGraphAndPopulate([dependsOnFinalized])

        then:
        executedTasks == [finalized, finalizer, dependsOnFinalized]
    }

    def "finalizer tasks run as soon as possible for tasks that must run after finalized tasks"() {
        Task finalizer = task("finalizer")
        Task finalized = task("finalized", finalizedBy: [finalizer])
        Task mustRunAfterFinalized = task("mustRunAfterFinalized", mustRunAfter: [finalized])

        when:
        addToGraphAndPopulate([mustRunAfterFinalized, finalized])

        then:
        executedTasks == [finalized, finalizer, mustRunAfterFinalized]
    }

    def "getAllTasks returns tasks in execution order"() {
        Task e = task("e");
        Task d = task("d", mustRunAfter: [e]);
        Task c = task("c");
        Task b = task("b", dependsOn: [d, c, e]);
        Task a = task("a", dependsOn: [b]);

        when:
        addToGraphAndPopulate(toList(a));

        then:
        executionPlan.getTasks() == [c, e, d, b, a]
        executedTasks == [c, e, d, b, a]
    }

    def "cannot add task with circular reference"() {
        Task a = createTask("a")
        Task b = task("b", dependsOn: [a])
        Task c = task("c", dependsOn: [b])
        Task d = task("d")
        relationships(a, dependsOn: [c, d])

        when:
        addToGraphAndPopulate([c])

        then:
        def e = thrown CircularReferenceException
        e.message == TextUtil.toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "cannot add a task with must run after induced circular reference"() {
        Task a = createTask("a")
        Task b = task("b", mustRunAfter: [a])
        Task c = task("c", dependsOn: [b])
        relationships(a, dependsOn: [c])

        when:
        addToGraphAndPopulate([a])

        then:
        def e = thrown CircularReferenceException
        e.message == TextUtil.toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "cannot add a task with must run after induced circular reference that was previously in graph but not required"() {
        Task a = createTask("a")
        Task b = task("b", mustRunAfter: [a])
        Task c = task("c", dependsOn: [b])
        Task d = task("d", dependsOn: [c])
        relationships(a, mustRunAfter: [c])
        executionPlan.addToTaskGraph([d])

        when:
        executionPlan.addToTaskGraph([a])
        executionPlan.determineExecutionPlan()

        then:
        def e = thrown CircularReferenceException
        e.message == TextUtil.toPlatformLineSeparators("""Circular dependency between the following tasks:
:a
\\--- :c
     \\--- :b
          \\--- :a (*)

(*) - details omitted (listed previously)
""")
    }

    def "stops returning tasks on task execution failure"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = task("a");
        Task b = task("b");
        addToGraphAndPopulate([a, b])

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
        executionPlan.getTaskToExecute()
    }

    def "stops returning tasks on first task failure when no failure handler provided"() {
        RuntimeException failure = new RuntimeException("failure");
        Task a = task("a", failure: failure);
        Task b = task("b");

        when:
        addToGraphAndPopulate([a, b])

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
        Task a = task("a", failure: failure);
        Task b = task("b");

        addToGraphAndPopulate([a, b])

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
        Task a = task("a", failure: failure);
        Task b = task("b");
        addToGraphAndPopulate([a, b])

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

    def "continues to return tasks when failure handler does not abort execution and task are mustRunAfter dependent"() {
        RuntimeException failure = new RuntimeException();
        Task a = task("a", failure: failure);
        Task b = task("b", mustRunAfter: [a]);
        addToGraphAndPopulate([a, b])

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
        final Task a = task("a", failure: failure)
        final Task b = task("b", dependsOn: [a])
        final Task c = task("c")
        addToGraphAndPopulate([b, c])

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
        addToGraphAndPopulate(toList(a));
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
        addToGraphAndPopulate([a])

        then:
        executedTasks == [a]

        when:
        executionPlan.clear()
        addToGraphAndPopulate([b])

        then:
        executedTasks == [b]
    }

    def "does not build graph for or execute filtered tasks"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b")
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.useFilter(filter);
        addToGraphAndPopulate([a, b])

        then:
        executionPlan.getTasks() == [b]
        executedTasks == [b]
    }

    def "does not build graph for or execute filtered dependencies"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b")
        Task c = task("c", dependsOn: [a, b])
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.useFilter(filter)
        addToGraphAndPopulate([c])

        then:
        executionPlan.tasks == [b, c]
        executedTasks == [b, c]
    }

    def "does not build graph for or execute filtered tasks reachable via task ordering"() {
        given:
        Task a = filteredTask("a")
        Task b = task("b", mustRunAfter: [a])
        Task c = task("c", dependsOn: [a])
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != a }

        when:
        executionPlan.useFilter(filter)
        addToGraphAndPopulate([b, c])

        then:
        executionPlan.tasks == [b, c]
        executedTasks == [b, c]
    }

    def "will execute a task whose dependencies have been filtered"() {
        given:
        Task b = filteredTask("b")
        Task c = task("c", dependsOn: [b])
        Spec<Task> filter = Mock()

        and:
        filter.isSatisfiedBy(_) >> { Task t -> t != b }

        when:
        executionPlan.useFilter(filter)
        addToGraphAndPopulate([c]);

        then:
        executedTasks == [c]
    }

    def "one parallel task per project is allowed"() {
        given:
        //2 projects, 2 tasks each
        def projectA = createChildProject(root, "a")
        def projectB = createChildProject(root, "b")

        def fooA = projectA.task("foo")
        def barA = projectA.task("bar")

        def fooB = projectB.task("foo")
        def barB = projectB.task("bar")

        addToGraphAndPopulate([fooA, barA, fooB, barB])

        when:
        //simulate build with 4 parallel threads
        List<TaskInfo> executing = []
        def t1 = concurrent.start { executing << executionPlan.getTaskToExecute() }
        def t2 = concurrent.start { executing << executionPlan.getTaskToExecute() }
        concurrent.finished() //wait for first 2 threads to get tasks
        def t3 = concurrent.start { executing << executionPlan.getTaskToExecute() }
        def t4 = concurrent.start { executing << executionPlan.getTaskToExecute() }

        then:
        //tasks from different projects were retrieved
        assert executing*.task.project as Set == [projectA, projectB] as Set
        //3rd,4th threads are still waiting for tasks
        t3.running()
        t4.running()

        when: //complete first round of tasks
        concurrent.start { executionPlan.taskComplete(executing[0]) }
        concurrent.start { executionPlan.taskComplete(executing[1]) }
        concurrent.finished()

        then: //all tasks started
        executing*.task as Set == [fooA, fooB, barA, barB] as Set

        when: //complete second round of tasks
        concurrent.start { executionPlan.taskComplete(executing[2]) }
        concurrent.start { executionPlan.taskComplete(executing[3]) }
        concurrent.finished()

        then:
        executionPlan.getTaskToExecute() == null
    }

    private TaskDependency taskDependencyResolvingTo(TaskInternal task, List<Task> tasks) {
        Mock(TaskDependency) {
            getDependencies(task) >> tasks
        }
    }

    private TaskDependency brokenDependencies() {
        Mock(TaskDependency) {
            0 * getDependencies(_)
        }
    }

    private void dependsOn(TaskInternal task, List<Task> dependsOnTasks) {
        task.getTaskDependencies() >> taskDependencyResolvingTo(task, dependsOnTasks)
    }

    private void mustRunAfter(TaskInternal task, List<Task> mustRunAfterTasks) {
        task.getMustRunAfter() >> taskDependencyResolvingTo(task, mustRunAfterTasks)
    }

    private void finalizedBy(TaskInternal task, List<Task> finalizedByTasks) {
        task.getFinalizedBy() >> taskDependencyResolvingTo(task, finalizedByTasks)
    }

    private void failure(TaskInternal task, final RuntimeException failure) {
        task.state.getFailure() >> failure
        task.state.rethrowFailure() >> { throw failure }
    }
    
    private TaskInternal task(final String name) {
        task([:], name)
    }

    private TaskInternal task(Map options, final String name) {
        def task = createTask(name)
        relationships(options, task)
        if (options.failure) {
            failure(task, options.failure)
        }
        task.getDidWork() >> (options.containsKey('didWork') ? options.didWork : true)
        return task
    }

    private void relationships(Map options, TaskInternal task) {
        dependsOn(task, options.dependsOn ?: [])
        mustRunAfter(task, options.mustRunAfter ?: [])
        finalizedBy(task, options.finalizedBy ?: [])
    }

    private TaskInternal filteredTask(final String name) {
        def task = createTask(name);
        task.getTaskDependencies() >> brokenDependencies()
        task.getMustRunAfter() >> brokenDependencies()
        task.getFinalizedBy() >> taskDependencyResolvingTo(task, [])
        return task
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
}

