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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskProperties;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraph;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.graph.GraphNodeRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.resources.ResourceDeadlockException;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A reusable implementation of TaskExecutionPlan. The {@link #addToTaskGraph(java.util.Collection)} and {@link #clear()} methods are NOT threadsafe, and callers must synchronize access to these
 * methods.
 */
@NonNullApi
public class DefaultTaskExecutionPlan implements TaskExecutionPlan {
    private final Set<TaskInfo> tasksInUnknownState = new LinkedHashSet<TaskInfo>();
    private final Set<TaskInfo> entryTasks = new LinkedHashSet<TaskInfo>();
    private final LinkedHashMap<Task, TaskInfo> executionPlan = new LinkedHashMap<Task, TaskInfo>();
    private final List<TaskInfo> executionQueue = new LinkedList<TaskInfo>();
    private final Map<Project, ResourceLock> projectLocks = Maps.newHashMap();
    private final TaskFailureCollector failureCollector = new TaskFailureCollector();
    private final TaskInfoFactory nodeFactory = new TaskInfoFactory(failureCollector);
    private Spec<? super Task> filter = Specs.satisfyAll();

    private boolean continueOnFailure;

    private final Set<TaskInfo> runningTasks = Sets.newIdentityHashSet();
    private final Set<Task> filteredTasks = Sets.newIdentityHashSet();
    private final Map<TaskInfo, TaskMutationInfo> taskMutations = Maps.newIdentityHashMap();
    private final Map<File, String> canonicalizedFileCache = Maps.newIdentityHashMap();
    private final Map<Pair<TaskInfo, TaskInfo>, Boolean> reachableCache = Maps.newHashMap();
    private final Set<TaskInfo> dependenciesCompleteCache = Sets.newHashSet();
    private final WorkerLeaseService workerLeaseService;
    private final GradleInternal gradle;

    private boolean tasksCancelled;

    public DefaultTaskExecutionPlan(WorkerLeaseService workerLeaseService, GradleInternal gradle) {
        this.workerLeaseService = workerLeaseService;
        this.gradle = gradle;
    }

    @Override
    public String getDisplayName() {
        Path path = gradle.findIdentityPath();
        if (path == null) {
            return "gradle";
        }
        return path.toString();
    }

    public void addToTaskGraph(Collection<? extends Task> tasks) {
        List<TaskInfo> queue = new ArrayList<TaskInfo>();

        List<Task> sortedTasks = new ArrayList<Task>(tasks);
        Collections.sort(sortedTasks);
        for (Task task : sortedTasks) {
            TaskInfo node = nodeFactory.getOrCreateNode(task);
            if (node.isMustNotRun()) {
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
                filteredTasks.add(task);
                continue;
            }

            if (visiting.add(node)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                // Make sure it has been configured
                ((TaskContainerInternal) task.getProject().getTasks()).prepareForExecution(task);
                Set<? extends Task> dependsOnTasks = context.getDependencies(task, task.getTaskDependencies());
                for (Task dependsOnTask : dependsOnTasks) {
                    TaskInfo targetNode = nodeFactory.getOrCreateNode(dependsOnTask);
                    node.addDependencySuccessor(targetNode);
                    if (!visiting.contains(targetNode)) {
                        queue.add(0, targetNode);
                    }
                }
                for (Task finalizerTask : context.getDependencies(task, task.getFinalizedBy())) {
                    TaskInfo targetNode = nodeFactory.getOrCreateNode(finalizerTask);
                    addFinalizerNode(node, targetNode);
                    if (!visiting.contains(targetNode)) {
                        queue.add(0, targetNode);
                    }
                }
                for (Task mustRunAfter : context.getDependencies(task, task.getMustRunAfter())) {
                    TaskInfo targetNode = nodeFactory.getOrCreateNode(mustRunAfter);
                    node.addMustSuccessor(targetNode);
                }
                for (Task shouldRunAfter : context.getDependencies(task, task.getShouldRunAfter())) {
                    TaskInfo targetNode = nodeFactory.getOrCreateNode(shouldRunAfter);
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
                    assert predecessor.isRequired() || predecessor.isMustNotRun();
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
        }
    }

    private <T> void addAllReversed(List<T> list, TreeSet<T> set) {
        List<T> elements = CollectionUtils.toList(set);
        Collections.reverse(elements);
        list.addAll(elements);
    }

    private void requireWithDependencies(TaskInfo taskInfo) {
        if (taskInfo.isMustNotRun() && filter.isSatisfiedBy(taskInfo.getTask())) {
            taskInfo.require();
            for (TaskInfo dependency : taskInfo.getDependencySuccessors()) {
                requireWithDependencies(dependency);
            }
        }
    }

    public void determineExecutionPlan() {
        List<TaskInfoInVisitingSegment> nodeQueue = Lists.newArrayList(Iterables.transform(entryTasks, new Function<TaskInfo, TaskInfoInVisitingSegment>() {
            private int index;

            @Override
            public TaskInfoInVisitingSegment apply(TaskInfo taskInfo) {
                return new TaskInfoInVisitingSegment(taskInfo, index++);
            }
        }));
        int visitingSegmentCounter = nodeQueue.size();

        HashMultimap<TaskInfo, Integer> visitingNodes = HashMultimap.create();
        Deque<GraphEdge> walkedShouldRunAfterEdges = new ArrayDeque<GraphEdge>();
        Deque<TaskInfo> path = new ArrayDeque<TaskInfo>();
        HashMap<TaskInfo, Integer> planBeforeVisiting = new HashMap<TaskInfo, Integer>();

        while (!nodeQueue.isEmpty()) {
            TaskInfoInVisitingSegment taskInfoInVisitingSegment = nodeQueue.get(0);
            int currentSegment = taskInfoInVisitingSegment.visitingSegment;
            TaskInfo taskNode = taskInfoInVisitingSegment.taskInfo;

            if (taskNode.isIncludeInGraph() || executionPlan.containsKey(taskNode.getTask())) {
                nodeQueue.remove(0);
                visitingNodes.remove(taskNode, currentSegment);
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, taskNode);
                continue;
            }

            boolean alreadyVisited = visitingNodes.containsKey(taskNode);
            visitingNodes.put(taskNode, currentSegment);

            if (!alreadyVisited) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                recordEdgeIfArrivedViaShouldRunAfter(walkedShouldRunAfterEdges, path, taskNode);
                removeShouldRunAfterSuccessorsIfTheyImposeACycle(visitingNodes, taskInfoInVisitingSegment);
                takePlanSnapshotIfCanBeRestoredToCurrentTask(planBeforeVisiting, taskNode);
                ArrayList<TaskInfo> successors = new ArrayList<TaskInfo>();
                addAllSuccessorsInReverseOrder(taskNode, successors);
                for (TaskInfo successor : successors) {
                    if (visitingNodes.containsEntry(successor, currentSegment)) {
                        if (!walkedShouldRunAfterEdges.isEmpty()) {
                            //remove the last walked should run after edge and restore state from before walking it
                            GraphEdge toBeRemoved = walkedShouldRunAfterEdges.pop();
                            toBeRemoved.from.removeShouldRunAfterSuccessor(toBeRemoved.to);
                            restorePath(path, toBeRemoved);
                            restoreQueue(nodeQueue, visitingNodes, toBeRemoved);
                            restoreExecutionPlan(planBeforeVisiting, toBeRemoved);
                            break;
                        } else {
                            onOrderingCycle();
                        }
                    }
                    nodeQueue.add(0, new TaskInfoInVisitingSegment(successor, currentSegment));
                }
                path.push(taskNode);
            } else {
                // Have visited this task's dependencies - add it to the end of the plan
                nodeQueue.remove(0);
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, taskNode);
                visitingNodes.remove(taskNode, currentSegment);
                path.pop();
                executionPlan.put(taskNode.getTask(), taskNode);
                Project project = taskNode.getTask().getProject();
                projectLocks.put(project, getOrCreateProjectLock(project));

                TaskMutationInfo taskMutationInfo = getOrCreateMutationsOf(taskNode);

                for (TaskInfo dependency : taskNode.getDependencySuccessors()) {
                    getOrCreateMutationsOf(dependency).consumingTasks.add(taskNode);
                    taskMutationInfo.consumesOutputOf.add(dependency);
                }

                // Add any finalizers to the queue
                ArrayList<TaskInfo> finalizerTasks = new ArrayList<TaskInfo>();
                addAllReversed(finalizerTasks, taskNode.getFinalizers());
                for (TaskInfo finalizer : finalizerTasks) {
                    if (!visitingNodes.containsKey(finalizer)) {
                        nodeQueue.add(finalizerTaskPosition(finalizer, nodeQueue), new TaskInfoInVisitingSegment(finalizer, visitingSegmentCounter++));
                    }
                }
            }
        }
        executionQueue.clear();
        executionQueue.addAll(executionPlan.values());

    }

    @Override
    public Set<Task> getDependencies(Task task) {
        TaskInfo node = executionPlan.get(task);
        if (node == null) {
            throw new IllegalStateException("Task is not part of the execution plan, no dependency information is available.");
        }
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (TaskInfo taskInfo : node.getDependencySuccessors()) {
            builder.add(taskInfo.getTask());
        }
        return builder.build();
    }

    private TaskMutationInfo getOrCreateMutationsOf(TaskInfo taskInfo) {
        TaskMutationInfo mutations = taskMutations.get(taskInfo);
        if (mutations == null) {
            mutations = new TaskMutationInfo(taskInfo);
            taskMutations.put(taskInfo, mutations);
        }
        return mutations;
    }

    private void maybeRemoveProcessedShouldRunAfterEdge(Deque<GraphEdge> walkedShouldRunAfterEdges, TaskInfo taskNode) {
        if (!walkedShouldRunAfterEdges.isEmpty() && walkedShouldRunAfterEdges.peek().to.equals(taskNode)) {
            walkedShouldRunAfterEdges.pop();
        }
    }

    private void restoreExecutionPlan(HashMap<TaskInfo, Integer> planBeforeVisiting, GraphEdge toBeRemoved) {
        Iterator<Map.Entry<Task, TaskInfo>> executionPlanIterator = executionPlan.entrySet().iterator();
        for (int i = 0; i < planBeforeVisiting.get(toBeRemoved.from); i++) {
            executionPlanIterator.next();
        }
        while (executionPlanIterator.hasNext()) {
            executionPlanIterator.next();
            executionPlanIterator.remove();
        }
    }

    private void restoreQueue(List<TaskInfoInVisitingSegment> nodeQueue, HashMultimap<TaskInfo, Integer> visitingNodes, GraphEdge toBeRemoved) {
        TaskInfoInVisitingSegment nextInQueue = null;
        while (nextInQueue == null || !toBeRemoved.from.equals(nextInQueue.taskInfo)) {
            nextInQueue = nodeQueue.get(0);
            visitingNodes.remove(nextInQueue.taskInfo, nextInQueue.visitingSegment);
            if (!toBeRemoved.from.equals(nextInQueue.taskInfo)) {
                nodeQueue.remove(0);
            }
        }
    }

    private void restorePath(Deque<TaskInfo> path, GraphEdge toBeRemoved) {
        TaskInfo removedFromPath = null;
        while (!toBeRemoved.from.equals(removedFromPath)) {
            removedFromPath = path.pop();
        }
    }

    private void addAllSuccessorsInReverseOrder(TaskInfo taskNode, ArrayList<TaskInfo> dependsOnTasks) {
        addAllReversed(dependsOnTasks, taskNode.getDependencySuccessors());
        addAllReversed(dependsOnTasks, taskNode.getMustSuccessors());
        addAllReversed(dependsOnTasks, taskNode.getFinalizingSuccessors());
        addAllReversed(dependsOnTasks, taskNode.getShouldSuccessors());
    }

    private void removeShouldRunAfterSuccessorsIfTheyImposeACycle(final HashMultimap<TaskInfo, Integer> visitingNodes, final TaskInfoInVisitingSegment taskNodeWithVisitingSegment) {
        TaskInfo taskNode = taskNodeWithVisitingSegment.taskInfo;
        Iterables.removeIf(taskNode.getShouldSuccessors(), new Predicate<TaskInfo>() {
            @Override
            public boolean apply(TaskInfo input) {
                return visitingNodes.containsEntry(input, taskNodeWithVisitingSegment.visitingSegment);
            }
        });
    }

    private void takePlanSnapshotIfCanBeRestoredToCurrentTask(HashMap<TaskInfo, Integer> planBeforeVisiting, TaskInfo taskNode) {
        if (taskNode.getShouldSuccessors().size() > 0) {
            planBeforeVisiting.put(taskNode, executionPlan.size());
        }
    }

    private void recordEdgeIfArrivedViaShouldRunAfter(Deque<GraphEdge> walkedShouldRunAfterEdges, Deque<TaskInfo> path, TaskInfo taskNode) {
        if (!path.isEmpty() && path.peek().getShouldSuccessors().contains(taskNode)) {
            walkedShouldRunAfterEdges.push(new GraphEdge(path.peek(), taskNode));
        }
    }

    /**
     * Given a finalizer task, determine where in the current node queue that it should be inserted.
     * The finalizer should be inserted after any of it's preceding tasks.
     */
    private int finalizerTaskPosition(TaskInfo finalizer, final List<TaskInfoInVisitingSegment> nodeQueue) {
        if (nodeQueue.size() == 0) {
            return 0;
        }

        Set<TaskInfo> precedingTasks = getAllPrecedingTasks(finalizer);
        Set<Integer> precedingTaskIndices = CollectionUtils.collect(precedingTasks, new Transformer<Integer, TaskInfo>() {
            @Override
            public Integer transform(final TaskInfo dependsOnTask) {
                return Iterables.indexOf(nodeQueue, new Predicate<TaskInfoInVisitingSegment>() {
                    @Override
                    public boolean apply(TaskInfoInVisitingSegment taskInfoInVisitingSegment) {
                        return taskInfoInVisitingSegment.taskInfo.equals(dependsOnTask);
                    }
                });
            }
        });
        return Collections.max(precedingTaskIndices) + 1;
    }

    private Set<TaskInfo> getAllPrecedingTasks(TaskInfo finalizer) {
        Set<TaskInfo> precedingTasks = new HashSet<TaskInfo>();
        Deque<TaskInfo> candidateTasks = new ArrayDeque<TaskInfo>();

        // Consider every task that must run before the finalizer
        candidateTasks.addAll(finalizer.getDependencySuccessors());
        candidateTasks.addAll(finalizer.getMustSuccessors());
        candidateTasks.addAll(finalizer.getFinalizingSuccessors());
        candidateTasks.addAll(finalizer.getShouldSuccessors());

        // For each candidate task, add it to the preceding tasks.
        while (!candidateTasks.isEmpty()) {
            TaskInfo precedingTask = candidateTasks.pop();
            if (precedingTasks.add(precedingTask)) {
                // Any task that the preceding task must run after is also a preceding task.
                candidateTasks.addAll(precedingTask.getMustSuccessors());
            }
        }

        return precedingTasks;
    }

    private void onOrderingCycle() {
        CachingDirectedGraphWalker<TaskInfo, Void> graphWalker = new CachingDirectedGraphWalker<TaskInfo, Void>(new DirectedGraph<TaskInfo, Void>() {
            @Override
            public void getNodeValues(TaskInfo node, Collection<? super Void> values, Collection<? super TaskInfo> connectedNodes) {
                connectedNodes.addAll(node.getDependencySuccessors());
                connectedNodes.addAll(node.getMustSuccessors());
                connectedNodes.addAll(node.getFinalizingSuccessors());
            }
        });
        graphWalker.add(entryTasks);
        final List<TaskInfo> firstCycle = new ArrayList<TaskInfo>(graphWalker.findCycles().get(0));
        Collections.sort(firstCycle);

        DirectedGraphRenderer<TaskInfo> graphRenderer = new DirectedGraphRenderer<TaskInfo>(new GraphNodeRenderer<TaskInfo>() {
            @Override
            public void renderTo(TaskInfo node, StyledTextOutput output) {
                output.withStyle(StyledTextOutput.Style.Identifier).text(node.getTask().getIdentityPath());
            }
        }, new DirectedGraph<TaskInfo, Object>() {
            @Override
            public void getNodeValues(TaskInfo node, Collection<? super Object> values, Collection<? super TaskInfo> connectedNodes) {
                for (TaskInfo dependency : firstCycle) {
                    if (node.getDependencySuccessors().contains(dependency) || node.getMustSuccessors().contains(dependency) || node.getFinalizingSuccessors().contains(dependency)) {
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
        nodeFactory.clear();
        entryTasks.clear();
        executionPlan.clear();
        executionQueue.clear();
        projectLocks.clear();
        failureCollector.clearFailures();
        taskMutations.clear();
        canonicalizedFileCache.clear();
        reachableCache.clear();
        dependenciesCompleteCache.clear();
        runningTasks.clear();
    }

    @Override
    public List<Task> getTasks() {
        return new ArrayList<Task>(executionPlan.keySet());
    }

    @Override
    public Set<Task> getFilteredTasks() {
        return filteredTasks;
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public void setContinueOnFailure(boolean continueOnFailre) {
        this.continueOnFailure = continueOnFailre;
    }

    @Override
    @Nullable
    public TaskInfo selectNextTask(WorkerLeaseRegistry.WorkerLease workerLease, ResourceLockState resourceLockState) {
        if (allProjectsLocked()) {
            return null;
        }

        Iterator<TaskInfo> iterator = executionQueue.iterator();
        while (iterator.hasNext()) {
            TaskInfo taskInfo = iterator.next();
            if (taskInfo.isReady() && allDependenciesComplete(taskInfo)) {
                ResourceLock projectLock = getProjectLock(taskInfo);
                TaskMutationInfo taskMutationInfo = getResolvedTaskMutationInfo(taskInfo);

                // TODO: convert output file checks to a resource lock
                if (!projectLock.tryLock() || !workerLease.tryLock() || !canRunWithCurrentlyExecutedTasks(taskInfo, taskMutationInfo)) {
                    resourceLockState.releaseLocks();
                    continue;
                }

                if (taskInfo.allDependenciesSuccessful()) {
                    recordTaskStarted(taskInfo);
                    taskInfo.startExecution();
                } else {
                    taskInfo.skipExecution();
                }
                iterator.remove();

                return taskInfo;
            }
        }
        return null;
    }

    private TaskMutationInfo getResolvedTaskMutationInfo(TaskInfo taskInfo) {
        TaskInternal task = taskInfo.getTask();
        TaskMutationInfo taskMutationInfo = taskMutations.get(taskInfo);
        if (!taskMutationInfo.resolved) {
            ProjectInternal project = (ProjectInternal) task.getProject();
            ServiceRegistry serviceRegistry = project.getServices();
            PathToFileResolver resolver = serviceRegistry.get(PathToFileResolver.class);
            PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);
            TaskProperties taskProperties = DefaultTaskProperties.resolve(propertyWalker, resolver, task);
            taskMutationInfo.outputPaths.addAll(getOutputPaths(canonicalizedFileCache, taskInfo, taskProperties.getOutputFiles(), taskProperties.getLocalStateFiles()));
            taskMutationInfo.destroyablePaths.addAll(getDestroyablePaths(canonicalizedFileCache, taskInfo, taskProperties.getDestroyableFiles()));
            taskMutationInfo.hasFileInputs = !taskProperties.getInputFileProperties().isEmpty();
            taskMutationInfo.hasOutputs = taskProperties.hasDeclaredOutputs();
            taskMutationInfo.hasLocalState = !taskProperties.getLocalStateFiles().isEmpty();
            taskMutationInfo.resolved = true;

            if (!taskMutationInfo.destroyablePaths.isEmpty()) {
                if (taskMutationInfo.hasOutputs) {
                    throw new IllegalStateException("Task " + taskInfo.getTask().getIdentityPath() + " has both outputs and destroyables defined.  A task can define either outputs or destroyables, but not both.");
                }
                if (taskMutationInfo.hasFileInputs) {
                    throw new IllegalStateException("Task " + taskInfo.getTask().getIdentityPath() + " has both inputs and destroyables defined.  A task can define either inputs or destroyables, but not both.");
                }
                if (taskMutationInfo.hasLocalState) {
                    throw new IllegalStateException("Task " + taskInfo.getTask().getIdentityPath() + " has both local state and destroyables defined.  A task can define either local state or destroyables, but not both.");
                }
            }
        }
        return taskMutationInfo;
    }

    private boolean allDependenciesComplete(TaskInfo taskInfo) {
        if (dependenciesCompleteCache.contains(taskInfo)) {
            return true;
        }

        boolean dependenciesComplete = taskInfo.allDependenciesComplete();
        if (dependenciesComplete) {
            dependenciesCompleteCache.add(taskInfo);
        }

        return dependenciesComplete;
    }

    private boolean allProjectsLocked() {
        for (ResourceLock lock : projectLocks.values()) {
            if (!lock.isLocked()) {
                return false;
            }
        }
        return true;
    }

    private ResourceLock getProjectLock(TaskInfo taskInfo) {
        return projectLocks.get(taskInfo.getTask().getProject());
    }

    private ResourceLock getOrCreateProjectLock(Project project) {
        String gradlePath = ((GradleInternal) project.getGradle()).getIdentityPath().toString();
        String projectPath = ((ProjectInternal) project).getIdentityPath().toString();
        return workerLeaseService.getProjectLock(gradlePath, projectPath);
    }

    private boolean canRunWithCurrentlyExecutedTasks(TaskInfo taskInfo, TaskMutationInfo taskMutationInfo) {
        Set<String> candidateTaskDestroyables = taskMutationInfo.destroyablePaths;

        if (!runningTasks.isEmpty()) {
            Set<String> candidateTaskOutputs = taskMutationInfo.outputPaths;
            Set<String> candidateTaskMutations = !candidateTaskOutputs.isEmpty() ? candidateTaskOutputs : candidateTaskDestroyables;
            if (hasTaskWithOverlappingMutations(candidateTaskMutations)) {
                return false;
            }
        }

        return !doesDestroyNotYetConsumedOutputOfAnotherTask(taskInfo, candidateTaskDestroyables);
    }

    private static ImmutableSet<String> canonicalizedPaths(final Map<File, String> cache, Iterable<File> files) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (File file : files) {
            String path;
            try {
                path = cache.get(file);
                if (path == null) {
                    path = file.getCanonicalPath();
                    cache.put(file, path);
                }
                builder.add(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return builder.build();
    }

    private boolean hasTaskWithOverlappingMutations(Set<String> candidateTaskMutations) {
        if (!candidateTaskMutations.isEmpty()) {
            for (TaskInfo runningTask : runningTasks) {
                TaskMutationInfo taskMutationInfo = taskMutations.get(runningTask);
                Iterable<String> runningTaskMutations = Iterables.concat(taskMutationInfo.outputPaths, taskMutationInfo.destroyablePaths);
                if (hasOverlap(candidateTaskMutations, runningTaskMutations)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean doesDestroyNotYetConsumedOutputOfAnotherTask(TaskInfo destroyerTask, Set<String> destroyablePaths) {
        if (!destroyablePaths.isEmpty()) {
            for (TaskMutationInfo producingTask : taskMutations.values()) {
                if (!producingTask.task.isComplete()) {
                    // We don't care about producing tasks that haven't finished yet
                    continue;
                }
                if (producingTask.consumingTasks.isEmpty()) {
                    // We don't care about tasks whose output is not consumed by anyone anymore
                    continue;
                }
                if (!hasOverlap(destroyablePaths, producingTask.outputPaths)) {
                    // No overlap no cry
                    continue;
                }
                for (TaskInfo consumingTask : producingTask.consumingTasks) {
                    if (doesConsumerDependOnDestroyer(consumingTask, destroyerTask)) {
                        // If there's an explicit dependency from consuming task to destroyer,
                        // then we accept that as the will of the user
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean doesConsumerDependOnDestroyer(TaskInfo consumingTask, TaskInfo destroyerTask) {
        if (consumingTask == destroyerTask) {
            return true;
        }
        Pair<TaskInfo, TaskInfo> taskPair = Pair.of(consumingTask, destroyerTask);
        if (reachableCache.get(taskPair) != null) {
            return reachableCache.get(taskPair);
        }

        boolean reachable = false;
        for (TaskInfo dependency : Iterables.concat(consumingTask.getMustSuccessors(), consumingTask.getFinalizingSuccessors(), consumingTask.getDependencySuccessors())) {
            if (!dependency.isComplete()) {
                if (doesConsumerDependOnDestroyer(dependency, destroyerTask)) {
                    reachable = true;
                }
            }
        }

        reachableCache.put(taskPair, reachable);
        return reachable;
    }

    private static boolean hasOverlap(Iterable<String> paths1, Iterable<String> paths2) {
        for (String path1 : paths1) {
            for (String path2 : paths2) {
                String overLappedPath = getOverLappedPath(path1, path2);
                if (overLappedPath != null) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Set<String> getOutputPaths(Map<File, String> canonicalizedFileCache, TaskInfo task, FileCollection outputFiles, FileCollection localStateFiles) {
        try {
            return canonicalizedPaths(canonicalizedFileCache, Iterables.concat(outputFiles, localStateFiles));
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(deadlockMessage(task, "an output or local state", "outputs"), e);
        }
    }

    private static Set<String> getDestroyablePaths(Map<File, String> canonicalizedFileCache, TaskInfo task, FileCollection destroyableFiles) {
        try {
            return canonicalizedPaths(canonicalizedFileCache, destroyableFiles);
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(deadlockMessage(task, "a destroyable", "destroyables"), e);
        }
    }

    private static String deadlockMessage(TaskInfo task, String singular, String plural) {
        return String.format("A deadlock was detected while resolving the %s for task '%s'. This can be caused, for instance, by %s property causing dependency resolution.", plural, task, singular);
    }

    @Nullable
    private static String getOverLappedPath(String firstPath, String secondPath) {
        if (firstPath.equals(secondPath)) {
            return firstPath;
        }
        if (firstPath.length() == secondPath.length()) {
            return null;
        }

        String shorter;
        String longer;
        if (firstPath.length() > secondPath.length()) {
            shorter = secondPath;
            longer = firstPath;
        } else {
            shorter = firstPath;
            longer = secondPath;
        }

        boolean isOverlapping = longer.startsWith(shorter) && longer.charAt(shorter.length()) == File.separatorChar;
        if (isOverlapping) {
            return shorter;
        } else {
            return null;
        }
    }

    private void recordTaskStarted(TaskInfo taskInfo) {
        runningTasks.add(taskInfo);
    }

    private void recordTaskCompleted(TaskInfo taskInfo) {
        runningTasks.remove(taskInfo);
        TaskMutationInfo taskMutationInfo = taskMutations.get(taskInfo);
        for (TaskInfo producerTask : taskMutationInfo.consumesOutputOf) {
            TaskMutationInfo producerTaskMutationInfo = taskMutations.get(producerTask);
            if (producerTaskMutationInfo.consumingTasks.remove(taskInfo) && canRemoveTaskMutation(producerTaskMutationInfo)) {
                taskMutations.remove(producerTask);
            }
        }

        if (canRemoveTaskMutation(taskMutationInfo)) {
            taskMutations.remove(taskInfo);
        }
    }

    private static boolean canRemoveTaskMutation(@Nullable TaskMutationInfo taskMutationInfo) {
        return taskMutationInfo != null && taskMutationInfo.task.isComplete() && taskMutationInfo.consumingTasks.isEmpty();
    }

    @Override
    public void taskComplete(TaskInfo taskInfo) {
        try {
            if (!taskInfo.isComplete()) {
                enforceFinalizerTasks(taskInfo);
                if (taskInfo.isFailed()) {
                    handleFailure(taskInfo);
                }

                taskInfo.finishExecution();
                recordTaskCompleted(taskInfo);
            }
        } finally {
            getProjectLock(taskInfo).unlock();
        }
    }

    private static void enforceFinalizerTasks(TaskInfo taskInfo) {
        for (TaskInfo finalizerNode : taskInfo.getFinalizers()) {
            if (finalizerNode.isRequired() || finalizerNode.isMustNotRun()) {
                enforceWithDependencies(finalizerNode, Sets.<TaskInfo>newHashSet());
            }
        }
    }

    private static void enforceWithDependencies(TaskInfo nodeInfo, Set<TaskInfo> enforcedTasks) {
        Deque<TaskInfo> candidateNodes = new ArrayDeque<TaskInfo>();
        candidateNodes.add(nodeInfo);

        while (!candidateNodes.isEmpty()) {
            TaskInfo node = candidateNodes.pop();
            if (!enforcedTasks.contains(node)) {
                enforcedTasks.add(node);

                candidateNodes.addAll(node.getDependencySuccessors());

                if (node.isMustNotRun() || node.isRequired()) {
                    node.enforceRun();
                }
            }
        }
    }

    @Override
    public void abortAllAndFail(Throwable t) {
        abortExecution(true);
        this.failureCollector.addFailure(t);
    }

    private void handleFailure(TaskInfo taskInfo) {
        Throwable executionFailure = taskInfo.getExecutionFailure();
        if (executionFailure != null) {
            // Always abort execution for an execution failure (as opposed to a task failure)
            abortExecution();
            this.failureCollector.addFailure(executionFailure);
            return;
        }

        // Task failure
        try {
            if (!continueOnFailure) {
                taskInfo.getTask().getState().rethrowFailure();
            }
            this.failureCollector.addFailure(taskInfo.getTaskFailure());
        } catch (Exception e) {
            // If the failure handler rethrows exception, then execution of other tasks is aborted. (--continue will collect failures)
            abortExecution();
            this.failureCollector.addFailure(e);
        }
    }

    private boolean abortExecution() {
        return abortExecution(false);
    }

    @Override
    public void cancelExecution() {
        tasksCancelled = abortExecution() || tasksCancelled;
    }

    private boolean abortExecution(boolean abortAll) {
        boolean aborted = false;
        for (TaskInfo taskInfo : executionPlan.values()) {
            // Allow currently executing and enforced tasks to complete, but skip everything else.
            if (taskInfo.isRequired()) {
                taskInfo.skipExecution();
                aborted = true;
            }

            // If abortAll is set, also stop enforced tasks.
            if (abortAll && taskInfo.isReady()) {
                taskInfo.abortExecution();
                aborted = true;
            }
        }
        return aborted;
    }

    @Override
    public void rethrowFailures() {
        if (tasksCancelled) {
            failureCollector.addFailure(new BuildCancelledException());
        }
        if (failureCollector.getFailures().isEmpty()) {
            return;
        }

        if (failureCollector.getFailures().size() > 1) {
            throw new MultipleBuildFailures(failureCollector.getFailures());
        }

        throw UncheckedException.throwAsUncheckedException(failureCollector.getFailures().get(0));
    }

    @Override
    public boolean allTasksComplete() {
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (!taskInfo.isComplete()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasWorkRemaining() {
        for (TaskInfo taskInfo : executionQueue) {
            if (!taskInfo.isComplete()) {
                return true;
            }
        }
        return false;
    }

    private static class GraphEdge {
        private final TaskInfo from;
        private final TaskInfo to;

        private GraphEdge(TaskInfo from, TaskInfo to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class TaskInfoInVisitingSegment {
        private final TaskInfo taskInfo;
        private final int visitingSegment;

        private TaskInfoInVisitingSegment(TaskInfo taskInfo, int visitingSegment) {
            this.taskInfo = taskInfo;
            this.visitingSegment = visitingSegment;
        }
    }

    private static class TaskMutationInfo {
        final TaskInfo task;
        final Set<TaskInfo> consumingTasks = Sets.newHashSet();
        final Set<TaskInfo> consumesOutputOf = Sets.newHashSet();
        final Set<String> outputPaths = Sets.newHashSet();
        final Set<String> destroyablePaths = Sets.newHashSet();
        boolean hasFileInputs;
        boolean hasOutputs;
        boolean hasLocalState;
        boolean resolved;

        TaskMutationInfo(TaskInfo task) {
            this.task = task;
        }
    }
}
