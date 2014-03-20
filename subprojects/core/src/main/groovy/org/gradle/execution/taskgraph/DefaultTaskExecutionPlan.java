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
        List<TaskInfo> queue = new ArrayList<TaskInfo>();

        List<Task> sortedTasks = new ArrayList<Task>(tasks);
        Collections.sort(sortedTasks);
        for (Task task : sortedTasks) {
            TaskInfo node = graph.addNode(task);
            if (node.getMustNotRun()) {
                requireWithDependencies(node);
            } else if (filter.isSatisfiedBy(task)) {
                node.require();
            }
            entryTasks.add(node);
            queue.add(node);
        }

        Set<TaskInfo> visiting = new HashSet<TaskInfo>();
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();

        while (!queue.isEmpty()) {
            TaskInfo node = queue.get(0);
            if (node.getDependenciesProcessed()) {
                // Have already visited this task - skip it
                queue.remove(0);
                continue;
            }

            TaskInternal task = node.getTask();
            boolean filtered = !filter.isSatisfiedBy(task);
            if (filtered) {
                // Task is not required - skip it
                queue.remove(0);
                node.dependenciesProcessed();
                node.doNotRequire();
                continue;
            }

            if (visiting.add(node)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                Set<? extends Task> dependsOnTasks = context.getDependencies(task);
                for (Task dependsOnTask : dependsOnTasks) {
                    TaskInfo targetNode = graph.addNode(dependsOnTask);
                    node.addDependencySuccessor(targetNode);
                    if (!visiting.contains(targetNode)) {
                        queue.add(0, targetNode);
                    }
                }
                for (Task finalizerTask : task.getFinalizedBy().getDependencies(task)) {
                    TaskInfo targetNode = graph.addNode(finalizerTask);
                    addFinalizerNode(node, targetNode);
                    if (!visiting.contains(targetNode)) {
                        queue.add(0, targetNode);
                    }
                }
                for (Task mustRunAfter : task.getMustRunAfter().getDependencies(task)) {
                    TaskInfo targetNode = graph.addNode(mustRunAfter);
                    node.addMustSuccessor(targetNode);
                }
                for (Task shouldRunAfter : task.getShouldRunAfter().getDependencies(task)) {
                    TaskInfo targetNode = graph.addNode(shouldRunAfter);
                    node.addShouldSuccessor(targetNode);
                }
                if (node.isRequired()) {
                    for (TaskInfo successor : node.getDependencySuccessors()) {
                        if (filter.isSatisfiedBy(successor.getTask())) {
                            successor.require();
                        }
                    }
                } else {
                    tasksInUnknownState.add(node);
                }
            } else {
                // Have visited this task's dependencies - add it to the graph
                queue.remove(0);
                visiting.remove(node);
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
                for (TaskInfo hardPredecessor : task.getDependencyPredecessors()) {
                    if (!visiting.contains(hardPredecessor)) {
                        queue.add(0, hardPredecessor);
                    }
                }
            } else {
                queue.remove(0);
                visiting.remove(task);
                task.mustNotRun();
                for (TaskInfo predecessor : task.getDependencyPredecessors()) {
                    assert predecessor.isRequired() || predecessor.getMustNotRun();
                    if (predecessor.isRequired()) {
                        task.require();
                        break;
                    }
                }
            }
        }
    }

    private void addFinalizerNode(TaskInfo node, TaskInfo finalizerNode) {
        if (filter.isSatisfiedBy(finalizerNode.getTask())) {
            node.addFinalizer(finalizerNode);
            if (!finalizerNode.isInKnownState()) {
                finalizerNode.mustNotRun();
            }
            finalizerNode.addMustSuccessor(node);
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
            for (TaskInfo dependency : taskInfo.getDependencySuccessors()) {
                requireWithDependencies(dependency);
            }
        }
    }

    public void determineExecutionPlan() {
        List<TaskInfo> nodeQueue = new ArrayList<TaskInfo>(entryTasks);
        Set<TaskInfo> visitingNodes = new HashSet<TaskInfo>();
        Stack<TaskDependencyGraphEdge> walkedShouldRunAfterEdges = new Stack<TaskDependencyGraphEdge>();
        Stack<TaskInfo> path = new Stack<TaskInfo>();
        HashMap<TaskInfo, Integer> planBeforeVisiting = new HashMap<TaskInfo, Integer>();
        while (!nodeQueue.isEmpty()) {
            TaskInfo taskNode = nodeQueue.get(0);

            if (taskNode.isIncludeInGraph() || executionPlan.containsKey(taskNode.getTask())) {
                nodeQueue.remove(0);
                continue;
            }

            if (visitingNodes.add(taskNode)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                recordEdgeIfArrivedViaShouldRunAfter(walkedShouldRunAfterEdges, path, taskNode);
                removeShouldRunAfterSuccessorsIfTheyImposeACycle(visitingNodes, taskNode);
                takePlanSnapshotIfCanBeRestoredToCurrentTask(planBeforeVisiting, taskNode);
                ArrayList<TaskInfo> successors = new ArrayList<TaskInfo>();
                addAllSuccessorsInReverseOrder(taskNode, successors);
                for (TaskInfo successor : successors) {
                    if (visitingNodes.contains(successor)) {
                        if (!walkedShouldRunAfterEdges.empty()) {
                            //remove the last walked should run after edge and restore state from before walking it
                            TaskDependencyGraphEdge toBeRemoved = walkedShouldRunAfterEdges.pop();
                            toBeRemoved.getFrom().removeShouldRunAfterSuccessor(toBeRemoved.getTo());
                            restorePath(path, toBeRemoved);
                            restoreQueue(nodeQueue, visitingNodes, toBeRemoved);
                            restoreExecutionPlan(planBeforeVisiting, toBeRemoved);
                            break;
                        } else {
                            onOrderingCycle();
                        }
                    }
                    nodeQueue.add(0, successor);
                }
                path.push(taskNode);
            } else {
                // Have visited this task's dependencies - add it to the end of the plan
                nodeQueue.remove(0);
                visitingNodes.remove(taskNode);
                path.pop();
                executionPlan.put(taskNode.getTask(), taskNode);
                // Add any finalizers to the queue
                ArrayList<TaskInfo> finalizerTasks = new ArrayList<TaskInfo>();
                addAllReversed(finalizerTasks, taskNode.getFinalizers());
                for (TaskInfo finalizer : finalizerTasks) {
                    if (!visitingNodes.contains(finalizer) && !nodeQueue.contains(finalizer)) {
                        nodeQueue.add(finalizerTaskPosition(finalizer, nodeQueue), finalizer);
                    }
                }
            }
        }
    }

    private void restoreExecutionPlan(HashMap<TaskInfo, Integer> planBeforeVisiting, TaskDependencyGraphEdge toBeRemoved) {
        Iterator<Map.Entry<Task, TaskInfo>> executionPlanIterator = executionPlan.entrySet().iterator();
        for (int i = 0; i < planBeforeVisiting.get(toBeRemoved.getFrom()); i++) {
            executionPlanIterator.next();
        }
        while (executionPlanIterator.hasNext()) {
            executionPlanIterator.next();
            executionPlanIterator.remove();
        }
    }

    private void restoreQueue(List<TaskInfo> nodeQueue, Set<TaskInfo> visitingNodes, TaskDependencyGraphEdge toBeRemoved) {
        TaskInfo nextInQueue = null;
        while (!toBeRemoved.getFrom().equals(nextInQueue)) {
            nextInQueue = nodeQueue.get(0);
            visitingNodes.remove(nextInQueue);
            if (!toBeRemoved.getFrom().equals(nextInQueue)) {
                nodeQueue.remove(0);
            }
        }
    }

    private void restorePath(Stack<TaskInfo> path, TaskDependencyGraphEdge toBeRemoved) {
        TaskInfo removedFromPath = null;
        while (!toBeRemoved.getFrom().equals(removedFromPath)) {
            removedFromPath = path.pop();
        }
    }

    private void addAllSuccessorsInReverseOrder(TaskInfo taskNode, ArrayList<TaskInfo> dependsOnTasks) {
        addAllReversed(dependsOnTasks, taskNode.getDependencySuccessors());
        addAllReversed(dependsOnTasks, taskNode.getMustSuccessors());
        addAllReversed(dependsOnTasks, taskNode.getShouldSuccessors());
    }

    private void removeShouldRunAfterSuccessorsIfTheyImposeACycle(Set<TaskInfo> visitingNodes, TaskInfo taskNode) {
        for (TaskInfo shouldRunAfterSuccessor : taskNode.getShouldSuccessors()) {
            if (visitingNodes.contains(shouldRunAfterSuccessor)) {
                taskNode.removeShouldRunAfterSuccessor(shouldRunAfterSuccessor);
            }
        }
    }

    private void takePlanSnapshotIfCanBeRestoredToCurrentTask(HashMap<TaskInfo, Integer> planBeforeVisiting, TaskInfo taskNode) {
        if (taskNode.getShouldSuccessors().size() > 0) {
            planBeforeVisiting.put(taskNode, executionPlan.size());
        }
    }

    private void recordEdgeIfArrivedViaShouldRunAfter(Stack<TaskDependencyGraphEdge> walkedShouldRunAfterEdges, Stack<TaskInfo> path, TaskInfo taskNode) {
        if (!path.empty() && path.peek().getShouldSuccessors().contains(taskNode)) {
            walkedShouldRunAfterEdges.push(new TaskDependencyGraphEdge(path.peek(), taskNode));
        }
    }

    private int finalizerTaskPosition(TaskInfo finalizer, final List<TaskInfo> nodeQueue) {
        if (nodeQueue.size() == 0) {
            return 0;
        }

        ArrayList<TaskInfo> dependsOnTasks = new ArrayList<TaskInfo>();
        dependsOnTasks.addAll(finalizer.getDependencySuccessors());
        dependsOnTasks.addAll(finalizer.getMustSuccessors());
        dependsOnTasks.addAll(finalizer.getShouldSuccessors());
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
                connectedNodes.addAll(node.getDependencySuccessors());
                connectedNodes.addAll(node.getMustSuccessors());
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
                    if (node.getDependencySuccessors().contains(dependency) || node.getMustSuccessors().contains(dependency)) {
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
        for (TaskInfo dependencyNode : node.getDependencySuccessors()) {
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
