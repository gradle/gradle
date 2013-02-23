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

import org.gradle.api.CircularReferenceException;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A reusable implementation of TaskExecutionPlan. The {@link #addToTaskGraph(java.util.Collection)} and {@link #clear()} methods are NOT threadsafe, and callers must synchronize
 * access to these methods.
 */
class DefaultTaskExecutionPlan implements TaskExecutionPlan {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<Task> entryTasks = new LinkedHashSet<Task>();
    private final TaskDependencyGraph graph = new TaskDependencyGraph();
    private final LinkedHashMap<Task, TaskInfo> executionPlan = new LinkedHashMap<Task, TaskInfo>();
    private final List<Throwable> failures = new ArrayList<Throwable>();
    private Spec<? super Task> filter = Specs.satisfyAll();

    private TaskFailureHandler failureHandler = new RethrowingFailureHandler();
    private final List<String> runningProjects = new ArrayList<String>();

    public void addToTaskGraph(Collection<? extends Task> tasks) {
        List<Task> queue = new ArrayList<Task>(tasks);
        Collections.sort(queue);
        entryTasks.addAll(queue);
        Set<Task> visiting = new HashSet<Task>();
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();

        while (!queue.isEmpty()) {
            Task task = queue.get(0);

            if (graph.hasTask(task) && graph.getNode(task).getRequired()) {
                queue.remove(0);
                continue;
            }

            if (visiting.add(task)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                Set<Task> dependsOnTasks = new TreeSet<Task>(Collections.reverseOrder());
                dependsOnTasks.addAll(context.getDependencies(task));
                for (Task dependsOnTask : dependsOnTasks) {
                    if (visiting.contains(dependsOnTask)) {
                        throw new CircularReferenceException(String.format(
                                "Circular dependency between tasks. Cycle includes [%s, %s].", task, dependsOnTask));
                    }
                    queue.add(0, dependsOnTask);
                }
            } else {
                // Have visited this task's dependencies - add it to the graph
                queue.remove(0);
                visiting.remove(task);
                Set<? extends Task> dependencies = context.getDependencies(task);
                for (Task dependency : dependencies) {
                    graph.addHardEdge(task, dependency);
                }
                if (dependencies.size() == 0) {
                    graph.addNode(task);
                }
                for (Task mustRunAfter : task.getMustRunAfter()) {
                    graph.addSoftEdge(task, mustRunAfter);
                }
            }
        }

        determineExecutionPlan();
    }

    private void determineExecutionPlan() {
        executionPlan.clear();

        List<TaskDependencyGraphNode> nodeQueue = CollectionUtils.collect(new ArrayList<Task>(entryTasks), new Transformer<TaskDependencyGraphNode, Task>() {
            public TaskDependencyGraphNode transform(Task original) {
                return graph.getNode(original);
            }
        });

        Set<TaskDependencyGraphNode> visitingNodes = new HashSet<TaskDependencyGraphNode>();
        while (!nodeQueue.isEmpty()) {
            TaskDependencyGraphNode taskNode = nodeQueue.get(0);
            if (!filter.isSatisfiedBy(taskNode.getTask()) || executionPlan.containsKey(taskNode.getTask()) || !taskNode.getRequired()) {
                // Already in plan - skip
                nodeQueue.remove(0);
                continue;
            }

            if (visitingNodes.add(taskNode)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                Set<TaskDependencyGraphNode> dependsOnTasks = new TreeSet<TaskDependencyGraphNode>(Collections.reverseOrder());
                dependsOnTasks.addAll(taskNode.getHardSuccessors());
                dependsOnTasks.addAll(taskNode.getSoftSuccessors());
                for (TaskDependencyGraphNode dependsOnTask : dependsOnTasks) {
                    if (visitingNodes.contains(dependsOnTask)) {
                        throw new CircularReferenceException(String.format(
                                "Circular dependency between tasks. Cycle includes [%s, %s].", taskNode.getTask(), dependsOnTask.getTask()));
                    }
                    nodeQueue.add(0, dependsOnTask);
                }
            } else {
                // Have visited this task's dependencies - add it to the end of the plan
                nodeQueue.remove(0);
                visitingNodes.remove(taskNode);
                Set<TaskInfo> dependencies = new HashSet<TaskInfo>();
                for (TaskDependencyGraphNode dependency : taskNode.getHardSuccessors()) {
                    TaskInfo dependencyInfo = executionPlan.get(dependency.getTask());
                    if (dependencyInfo != null) {
                        dependencies.add(dependencyInfo);
                    }
                    // else - the dependency has been filtered, so ignore it
                }
                executionPlan.put(taskNode.getTask(), new TaskInfo((TaskInternal) taskNode.getTask(), dependencies));
            }
        }
    }

    public void clear() {
        lock.lock();
        try {
            graph.clear();
            entryTasks.clear();
            executionPlan.clear();
            failures.clear();
            runningProjects.clear();
        } finally {
            lock.unlock();
        }
    }

    public List<Task> getTasks() {
        return new ArrayList<Task>(executionPlan.keySet());
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public void useFailureHandler(TaskFailureHandler handler) {
        this.failureHandler = handler;
    }

    public TaskInfo getTaskToExecute(Spec<TaskInfo> criteria) {
        lock.lock();
        try {

            TaskInfo nextMatching;
            while ((nextMatching = getNextReadyAndMatching(criteria)) != null) {
                while (!nextMatching.allDependenciesComplete()) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                // The task state could have been modified while we waited for dependency completion. Check that it is still 'ready'.
                if (!nextMatching.isReady()) {
                    continue;
                }

                if (nextMatching.allDependenciesSuccessful()) {
                    nextMatching.startExecution();
                    return nextMatching;
                } else {
                    nextMatching.skipExecution();
                    condition.signalAll();
                }
            }

            return null;

        } finally {
            lock.unlock();
        }
    }

    public TaskInfo getTaskToExecute() {
        lock.lock();
        try {
            while(true) {
                TaskInfo nextMatching = null;
                boolean allTasksComplete = true;
                for (TaskInfo taskInfo : executionPlan.values()) {
                    allTasksComplete = allTasksComplete && taskInfo.isComplete();
                    if (taskInfo.isReady() && taskInfo.allDependenciesComplete() && !runningProjects.contains(taskInfo.getTask().getProject().getPath())) {
                        nextMatching = taskInfo;
                        break;
                    }
                }
                if (allTasksComplete) {
                    return null;
                }
                if (nextMatching == null) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    if (nextMatching.allDependenciesSuccessful()) {
                        nextMatching.startExecution();
                        runningProjects.add(nextMatching.getTask().getProject().getPath());
                        return nextMatching;
                    } else {
                        nextMatching.skipExecution();
                        condition.signalAll();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private TaskInfo getNextReadyAndMatching(Spec<TaskInfo> criteria) {
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (taskInfo.isReady() && criteria.isSatisfiedBy(taskInfo)) {
                return taskInfo;
            }
        }
        return null;
    }

    public void taskComplete(TaskInfo taskInfo) {
        lock.lock();
        try {
            if (taskInfo.isFailed()) {
                handleFailure(taskInfo);
            }

            taskInfo.finishExecution();
            runningProjects.remove(taskInfo.getTask().getProject().getPath());
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void handleFailure(TaskInfo taskInfo) {
        Throwable executionFailure = taskInfo.getExecutionFailure();
        if (executionFailure != null) {
            // Always abort execution for an execution failure (as opposed to a task failure)
            abortExecution();
            this.failures.add(executionFailure);
            return;
        }

        // Task failure
        try {
            failureHandler.onTaskFailure(taskInfo.getTask());
            this.failures.add(taskInfo.getTaskFailure());
        } catch (Exception e) {
            // If the failure handler rethrows exception, then execution of other tasks is aborted. (--continue will collect failures)
            abortExecution();
            this.failures.add(e);
        }
    }

    private void abortExecution() {
        // Allow currently executing tasks to complete, but skip everything else.
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (taskInfo.isReady()) {
                taskInfo.skipExecution();
            }
        }
    }

    public void awaitCompletion() {
        lock.lock();
        try {
            while (!allTasksComplete()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            rethrowFailures();
        } finally {
            lock.unlock();
        }
    }

    private void rethrowFailures() {
        if (failures.isEmpty()) {
            return;
        }

        if (failures.size() > 1) {
            throw new MultipleBuildFailures(failures);
        }

        throw UncheckedException.throwAsUncheckedException(failures.get(0));
    }

    private boolean allTasksComplete() {
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (!taskInfo.isComplete()) {
                return false;
            }
        }
        return true;
    }

    private static class RethrowingFailureHandler implements TaskFailureHandler {
        public void onTaskFailure(Task task) {
            task.getState().rethrowFailure();
        }
    }
}
