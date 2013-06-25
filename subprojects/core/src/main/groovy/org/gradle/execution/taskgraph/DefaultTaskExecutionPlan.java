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
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraph;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.graph.GraphNodeRenderer;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.CollectionUtils;

import java.io.StringWriter;
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
    private final Set<TaskInfo> tasksInUnknownState = new LinkedHashSet<TaskInfo>();
    private final Set<TaskInfo> entryTasks = new LinkedHashSet<TaskInfo>();
    private final TaskDependencyGraph graph = new TaskDependencyGraph();
    private final LinkedHashMap<Task, TaskInfo> executionPlan = new LinkedHashMap<Task, TaskInfo>();
    private final List<Throwable> failures = new ArrayList<Throwable>();
    private Spec<? super Task> filter = Specs.satisfyAll();

    private TaskFailureHandler failureHandler = new RethrowingFailureHandler();
    private final List<String> runningProjects = new ArrayList<String>();

    public void addToTaskGraph(Collection<? extends Task> tasks) {
        List<Task> queue = new ArrayList<Task>(tasks);
        Collections.sort(queue);
        for (Task task : queue) {
            TaskInfo node = graph.addNode(task);
            if (node.getMustNotRun()) {
                requireWithDependencies(node);
            } else {
                node.require();
            }
            entryTasks.add(node);
        }
        Set<Task> visiting = new HashSet<Task>();
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();

        while (!queue.isEmpty()) {
            Task task = queue.get(0);
            TaskInfo node = graph.addNode(task);
            if (node.getDependenciesProcessed()) {
                // Have already visited this task - skip it
                queue.remove(0);
                continue;
            }
            boolean filtered = !filter.isSatisfiedBy(task);
            if (filtered) {
                // Task is not required - skip it
                queue.remove(0);
                node.dependenciesProcessed();
                node.doNotRequire();
                continue;
            }

            if (visiting.add(task)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                Set<? extends Task> dependsOnTasks = context.getDependencies(task);
                for (Task dependsOnTask : dependsOnTasks) {
                    graph.addHardEdge(node, dependsOnTask);
                    if (!visiting.contains(dependsOnTask)) {
                        queue.add(0, dependsOnTask);
                    }
                }
                for (Task finalizerTask : task.getFinalizedBy().getDependencies(task)) {
                    addFinalizerNode(node, finalizerTask);
                    if (!visiting.contains(finalizerTask)) {
                        queue.add(0, finalizerTask);
                    }
                }
                for (Task mustRunAfter : task.getMustRunAfter().getDependencies(task)) {
                    graph.addSoftEdge(node, mustRunAfter);
                }
                if (node.isRequired()) {
                    for (TaskInfo hardSuccessor : node.getHardSuccessors()) {
                        hardSuccessor.require();
                    }
                } else {
                    tasksInUnknownState.add(node);
                }
            } else {
                // Have visited this task's dependencies - add it to the graph
                queue.remove(0);
                visiting.remove(task);
                node.dependenciesProcessed();
            }
        }
        resolveTasksInUnknownState();
    }

    private void resolveTasksInUnknownState() {
        List<TaskInfo> queue = new ArrayList<TaskInfo>(tasksInUnknownState);
        Set<TaskInfo> visiting = new HashSet<TaskInfo>();

        while (!queue.isEmpty()) {
            TaskInfo task = queue.get(0);
            if (task.isInKnownState()) {
                queue.remove(0);
                continue;
            }

            if (visiting.add(task)) {
                for (TaskInfo hardPredecessor : task.getHardPredecessors()) {
                    if (!visiting.contains(hardPredecessor)) {
                        queue.add(0, hardPredecessor);
                    }
                }
            } else {
                queue.remove(0);
                visiting.remove(task);
                task.mustNotRun();
                for (TaskInfo predecessor : task.getHardPredecessors()) {
                    assert predecessor.isRequired() || predecessor.getMustNotRun();
                    if (predecessor.isRequired()) {
                        task.require();
                        break;
                    }
                }
            }
        }
    }

    private void addFinalizerNode(TaskInfo node, Task finalizerTask) {
        if (filter.isSatisfiedBy(finalizerTask)) {
            graph.addFinalizedByEdge(node, finalizerTask);
            TaskInfo finalizerNode = graph.getNode(finalizerTask);
            if (!finalizerNode.isInKnownState()) {
                finalizerNode.mustNotRun();
            }
            finalizerNode.addSoftSuccessor(node);
        }
    }

    private <T> void addAllReversed(List<T> list, TreeSet<T> set) {
        List<T> elements = CollectionUtils.toList(set);
        Collections.reverse(elements);
        list.addAll(elements);
    }

    private void requireWithDependencies(TaskInfo taskInfo) {
        if (taskInfo.getMustNotRun() && filter.isSatisfiedBy(taskInfo.getTask())) {
            taskInfo.require();
            for (TaskInfo dependency : taskInfo.getHardSuccessors()) {
                requireWithDependencies(dependency);
            }
        }
    }

    public void determineExecutionPlan() {
        List<TaskInfo> nodeQueue = new ArrayList<TaskInfo>(entryTasks);
        Set<TaskInfo> visitingNodes = new HashSet<TaskInfo>();
        while (!nodeQueue.isEmpty()) {
            TaskInfo taskNode = nodeQueue.get(0);

            if (taskNode.isNotRequired() || executionPlan.containsKey(taskNode.getTask())) {
                nodeQueue.remove(0);
                continue;
            }

            if (visitingNodes.add(taskNode)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                ArrayList<TaskInfo> dependsOnTasks = new ArrayList<TaskInfo>();
                addAllReversed(dependsOnTasks, taskNode.getHardSuccessors());
                addAllReversed(dependsOnTasks, taskNode.getSoftSuccessors());
                for (TaskInfo dependsOnTask : dependsOnTasks) {
                    if (visitingNodes.contains(dependsOnTask)) {
                        onOrderingCycle();
                    }
                    nodeQueue.add(0, dependsOnTask);
                }
            } else {
                // Have visited this task's dependencies - add it to the end of the plan
                nodeQueue.remove(0);
                visitingNodes.remove(taskNode);
                executionPlan.put(taskNode.getTask(), taskNode);
                ArrayList<TaskInfo> finalizerTasks = new ArrayList<TaskInfo>();
                addAllReversed(finalizerTasks, taskNode.getFinalizers());
                for (TaskInfo finalizer : finalizerTasks) {
                    if (!visitingNodes.contains(finalizer)) {
                        nodeQueue.add(finalizerTaskPosition(finalizer, nodeQueue), finalizer);
                    }
                }
            }
        }
    }

    private int finalizerTaskPosition(TaskInfo finalizer, final List<TaskInfo> nodeQueue) {
        if (nodeQueue.size() == 0) {
            return 0;
        }

        ArrayList<TaskInfo> dependsOnTasks = new ArrayList<TaskInfo>();
        dependsOnTasks.addAll(finalizer.getHardSuccessors());
        dependsOnTasks.addAll(finalizer.getSoftSuccessors());
        List<Integer> dependsOnTaskIndexes = CollectionUtils.collect(dependsOnTasks, new Transformer<Integer, TaskInfo>() {
            public Integer transform(TaskInfo dependsOnTask) {
                return nodeQueue.indexOf(dependsOnTask);
            }
        });
        return Collections.max(dependsOnTaskIndexes) + 1;
    }

    private void onOrderingCycle() {
        CachingDirectedGraphWalker<TaskInfo, Void> graphWalker = new CachingDirectedGraphWalker<TaskInfo, Void>(new DirectedGraph<TaskInfo, Void>() {
            public void getNodeValues(TaskInfo node, Collection<? super Void> values, Collection<? super TaskInfo> connectedNodes) {
                connectedNodes.addAll(node.getHardSuccessors());
                connectedNodes.addAll(node.getSoftSuccessors());
            }
        });
        graphWalker.add(entryTasks);
        final List<TaskInfo> firstCycle = new ArrayList<TaskInfo>(graphWalker.findCycles().get(0));
        Collections.sort(firstCycle);

        DirectedGraphRenderer<TaskInfo> graphRenderer = new DirectedGraphRenderer<TaskInfo>(new GraphNodeRenderer<TaskInfo>() {
            public void renderTo(TaskInfo node, StyledTextOutput output) {
                output.withStyle(StyledTextOutput.Style.Identifier).text(node.getTask().getPath());
            }
        }, new DirectedGraph<TaskInfo, Object>() {
            public void getNodeValues(TaskInfo node, Collection<? super Object> values, Collection<? super TaskInfo> connectedNodes) {
                for (TaskInfo dependency : firstCycle) {
                    if (node.getHardSuccessors().contains(dependency) || node.getSoftSuccessors().contains(dependency)) {
                        connectedNodes.add(dependency);
                    }
                }
            }
        });
        StringWriter writer = new StringWriter();
        graphRenderer.renderTo(firstCycle.get(0), writer);
        throw new CircularReferenceException(String.format("Circular dependency between the following tasks:%n%s", writer.toString()));
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

    public TaskInfo getTaskToExecute() {
        lock.lock();
        try {
            while (true) {
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

    public void taskComplete(TaskInfo taskInfo) {
        lock.lock();
        try {
            enforceFinalizerTasks(taskInfo);
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

    private void enforceFinalizerTasks(TaskInfo taskInfo) {
        for (TaskInfo finalizerNode : taskInfo.getFinalizers()) {
            if (finalizerNode.isRequired() || finalizerNode.getMustNotRun()) {
                enforceWithDependencies(finalizerNode);
            }
        }
    }

    private void enforceWithDependencies(TaskInfo node) {
        for (TaskInfo dependencyNode : node.getHardSuccessors()) {
            enforceWithDependencies(dependencyNode);
        }
        if (node.getMustNotRun() || node.isRequired()) {
            node.enforceRun();
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
        // Allow currently executing and enforced tasks to complete, but skip everything else.
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (taskInfo.isRequired()) {
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
