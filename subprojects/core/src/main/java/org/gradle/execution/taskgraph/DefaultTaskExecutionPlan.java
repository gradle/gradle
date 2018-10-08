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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskProperties;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Pair;
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
import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reusable implementation of TaskExecutionPlan. The {@link #addToTaskGraph(java.util.Collection)} and {@link #clear()} methods are NOT threadsafe, and callers must synchronize access to these methods.
 */
@NonNullApi
public class DefaultTaskExecutionPlan implements TaskExecutionPlan {
    private final Set<WorkInfo> workInUnknownState = Sets.newLinkedHashSet();
    private final Set<TaskInfo> entryTasks = new LinkedHashSet<TaskInfo>();
    private final WorkInfoMapping workInfoMapping = new WorkInfoMapping();
    private final List<WorkInfo> executionQueue = Lists.newLinkedList();
    private final Map<Project, ResourceLock> projectLocks = Maps.newHashMap();
    private final TaskFailureCollector failureCollector = new TaskFailureCollector();
    private final TaskInfoFactory nodeFactory;
    private final TaskDependencyResolver dependencyResolver;
    private Spec<? super Task> filter = Specs.satisfyAll();

    private boolean continueOnFailure;

    private final Set<WorkInfo> runningNodes = Sets.newIdentityHashSet();
    private final Set<WorkInfo> filteredNodes = Sets.newIdentityHashSet();
    private final Map<WorkInfo, MutationInfo> workMutations = Maps.newIdentityHashMap();
    private final Map<File, String> canonicalizedFileCache = Maps.newIdentityHashMap();
    private final Map<Pair<WorkInfo, WorkInfo>, Boolean> reachableCache = Maps.newHashMap();
    private final Set<WorkInfo> dependenciesCompleteCache = Sets.newHashSet();
    private final WorkerLeaseService workerLeaseService;
    private final GradleInternal gradle;

    private boolean tasksCancelled;

    public DefaultTaskExecutionPlan(WorkerLeaseService workerLeaseService, GradleInternal gradle, TaskInfoFactory taskInfoFactory, TaskDependencyResolver dependencyResolver) {
        this.workerLeaseService = workerLeaseService;
        this.gradle = gradle;
        this.nodeFactory = taskInfoFactory;
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public String getDisplayName() {
        Path path = gradle.findIdentityPath();
        if (path == null) {
            return "gradle";
        }
        return path.toString();
    }

    @VisibleForTesting
    TaskInfo getNode(Task task) {
        return workInfoMapping.get(task);
    }

    public void addToTaskGraph(Collection<? extends Task> tasks) {
        final Deque<WorkInfo> queue = new ArrayDeque<WorkInfo>();

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

        final Set<WorkInfo> visiting = Sets.newHashSet();

        while (!queue.isEmpty()) {
            WorkInfo node = queue.getFirst();
            if (node.getDependenciesProcessed()) {
                // Have already visited this task - skip it
                queue.removeFirst();
                continue;
            }

            boolean filtered = !nodeSatisfiesTaskFilter(node);
            if (filtered) {
                // Task is not required - skip it
                queue.removeFirst();
                node.dependenciesProcessed();
                node.doNotRequire();
                filteredNodes.add(node);
                continue;
            }

            if (visiting.add(node)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                // Make sure it has been configured
                node.prepareForExecution();
                node.resolveDependencies(dependencyResolver, new Action<WorkInfo>() {
                    @Override
                    public void execute(WorkInfo targetNode) {
                        if (!visiting.contains(targetNode)) {
                            queue.addFirst(targetNode);
                        }
                    }
                });
                if (node.isRequired()) {
                    for (WorkInfo successor : node.getDependencySuccessors()) {
                        if (nodeSatisfiesTaskFilter(successor)) {
                            successor.require();
                        }
                    }
                } else {
                    workInUnknownState.add(node);
                }
            } else {
                // Have visited this task's dependencies - add it to the graph
                queue.removeFirst();
                visiting.remove(node);
                node.dependenciesProcessed();
            }
        }
        resolveWorkInUnknownState();
    }

    private boolean nodeSatisfiesTaskFilter(WorkInfo successor) {
        if (successor instanceof LocalTaskInfo) {
            return filter.isSatisfiedBy(((LocalTaskInfo) successor).getTask());
        }
        return true;
    }

    private void resolveWorkInUnknownState() {
        List<WorkInfo> queue = Lists.newArrayList(workInUnknownState);
        Set<WorkInfo> visiting = Sets.newHashSet();

        while (!queue.isEmpty()) {
            WorkInfo workInfo = queue.get(0);
            if (workInfo.isInKnownState()) {
                queue.remove(0);
                continue;
            }

            if (visiting.add(workInfo)) {
                for (WorkInfo hardPredecessor : workInfo.getDependencyPredecessors()) {
                    if (!visiting.contains(hardPredecessor)) {
                        queue.add(0, hardPredecessor);
                    }
                }
            } else {
                queue.remove(0);
                visiting.remove(workInfo);
                workInfo.mustNotRun();
                for (WorkInfo predecessor : workInfo.getDependencyPredecessors()) {
                    assert predecessor.isRequired() || predecessor.isMustNotRun();
                    if (predecessor.isRequired()) {
                        workInfo.require();
                        break;
                    }
                }
            }
        }
    }

    private void requireWithDependencies(WorkInfo workInfo) {
        if (workInfo.isMustNotRun() && nodeSatisfiesTaskFilter(workInfo)) {
            workInfo.require();
            for (WorkInfo dependency : workInfo.getDependencySuccessors()) {
                requireWithDependencies(dependency);
            }
        }
    }

    public void determineExecutionPlan() {
        List<WorkInfoInVisitingSegment> nodeQueue = Lists.newArrayList(Iterables.transform(entryTasks, new Function<TaskInfo, WorkInfoInVisitingSegment>() {
            private int index;

            @Override
            @SuppressWarnings("NullableProblems")
            public WorkInfoInVisitingSegment apply(TaskInfo taskInfo) {
                return new WorkInfoInVisitingSegment(taskInfo, index++);
            }
        }));
        int visitingSegmentCounter = nodeQueue.size();

        HashMultimap<WorkInfo, Integer> visitingNodes = HashMultimap.create();
        Deque<GraphEdge> walkedShouldRunAfterEdges = new ArrayDeque<GraphEdge>();
        Deque<WorkInfo> path = new ArrayDeque<WorkInfo>();
        Map<WorkInfo, Integer> planBeforeVisiting = Maps.newHashMap();

        while (!nodeQueue.isEmpty()) {
            WorkInfoInVisitingSegment workInfoInVisitingSegment = nodeQueue.get(0);
            int currentSegment = workInfoInVisitingSegment.visitingSegment;
            WorkInfo workInfo = workInfoInVisitingSegment.workInfo;

            if (workInfo.isIncludeInGraph() || workInfoMapping.contains(workInfo)) {
                nodeQueue.remove(0);
                visitingNodes.remove(workInfo, currentSegment);
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, workInfo);
                continue;
            }

            boolean alreadyVisited = visitingNodes.containsKey(workInfo);
            visitingNodes.put(workInfo, currentSegment);

            if (!alreadyVisited) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                recordEdgeIfArrivedViaShouldRunAfter(walkedShouldRunAfterEdges, path, workInfo);
                removeShouldRunAfterSuccessorsIfTheyImposeACycle(visitingNodes, workInfoInVisitingSegment);
                takePlanSnapshotIfCanBeRestoredToCurrentTask(planBeforeVisiting, workInfo);

                for (WorkInfo successor : workInfo.getAllSuccessorsInReverseOrder()) {
                    if (visitingNodes.containsEntry(successor, currentSegment)) {
                        if (!walkedShouldRunAfterEdges.isEmpty()) {
                            //remove the last walked should run after edge and restore state from before walking it
                            GraphEdge toBeRemoved = walkedShouldRunAfterEdges.pop();
                            // Should run after edges only exist between tasks, so this cast is safe
                            TaskInfo sourceTask = (TaskInfo) toBeRemoved.from;
                            TaskInfo targetTask = (TaskInfo) toBeRemoved.to;
                            sourceTask.removeShouldSuccessor(targetTask);
                            restorePath(path, toBeRemoved);
                            restoreQueue(nodeQueue, visitingNodes, toBeRemoved);
                            restoreExecutionPlan(planBeforeVisiting, toBeRemoved);
                            break;
                        } else {
                            onOrderingCycle(successor, workInfo);
                        }
                    }
                    nodeQueue.add(0, new WorkInfoInVisitingSegment(successor, currentSegment));
                }
                path.push(workInfo);
            } else {
                // Have visited this node's dependencies - add it to the end of the plan
                nodeQueue.remove(0);
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, workInfo);
                visitingNodes.remove(workInfo, currentSegment);
                path.pop();
                workInfoMapping.add(workInfo);

                MutationInfo mutations = getOrCreateMutationsOf(workInfo);
                for (WorkInfo dependency : workInfo.getDependencySuccessors()) {
                    getOrCreateMutationsOf(dependency).consumingWork.add(workInfo);
                    mutations.consumesOutputOf.add(dependency);
                }

                if (workInfo instanceof LocalTaskInfo) {
                    LocalTaskInfo taskInfo = (LocalTaskInfo) workInfo;
                    TaskInternal task = taskInfo.getTask();
                    Project project = task.getProject();
                    projectLocks.put(project, getOrCreateProjectLock(project));

                    // Add any finalizers to the queue
                    for (WorkInfo finalizer : taskInfo.getFinalizers()) {
                        if (!visitingNodes.containsKey(finalizer)) {
                            nodeQueue.add(finalizerTaskPosition(finalizer, nodeQueue), new WorkInfoInVisitingSegment(finalizer, visitingSegmentCounter++));
                        }
                    }
                }
            }
        }
        executionQueue.clear();
        Iterables.addAll(executionQueue, workInfoMapping);
    }

    @Override
    public Set<Task> getDependencies(Task task) {
        TaskInfo node = workInfoMapping.get(task);
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (WorkInfo taskInfo : node.getDependencySuccessors()) {
            taskInfo.collectTaskInto(builder);
        }
        return builder.build();
    }

    private MutationInfo getOrCreateMutationsOf(WorkInfo workInfo) {
        MutationInfo mutations = this.workMutations.get(workInfo);
        if (mutations == null) {
            mutations = new MutationInfo(workInfo);
            this.workMutations.put(workInfo, mutations);
        }
        return mutations;
    }

    private void maybeRemoveProcessedShouldRunAfterEdge(Deque<GraphEdge> walkedShouldRunAfterEdges, WorkInfo workInfo) {
        GraphEdge edge = walkedShouldRunAfterEdges.peek();
        if (edge != null && edge.to.equals(workInfo)) {
            walkedShouldRunAfterEdges.pop();
        }
    }

    private void restoreExecutionPlan(Map<WorkInfo, Integer> planBeforeVisiting, GraphEdge toBeRemoved) {
        int count = planBeforeVisiting.get(toBeRemoved.from);
        workInfoMapping.retainFirst(count);
    }

    private void restoreQueue(List<WorkInfoInVisitingSegment> nodeQueue, HashMultimap<WorkInfo, Integer> visitingNodes, GraphEdge toBeRemoved) {
        WorkInfoInVisitingSegment nextInQueue = null;
        while (nextInQueue == null || !toBeRemoved.from.equals(nextInQueue.workInfo)) {
            nextInQueue = nodeQueue.get(0);
            visitingNodes.remove(nextInQueue.workInfo, nextInQueue.visitingSegment);
            if (!toBeRemoved.from.equals(nextInQueue.workInfo)) {
                nodeQueue.remove(0);
            }
        }
    }

    private void restorePath(Deque<WorkInfo> path, GraphEdge toBeRemoved) {
        WorkInfo removedFromPath = null;
        while (!toBeRemoved.from.equals(removedFromPath)) {
            removedFromPath = path.pop();
        }
    }

    private void removeShouldRunAfterSuccessorsIfTheyImposeACycle(final HashMultimap<WorkInfo, Integer> visitingNodes, final WorkInfoInVisitingSegment taskNodeWithVisitingSegment) {
        WorkInfo workInfo = taskNodeWithVisitingSegment.workInfo;
        if (!(workInfo instanceof TaskInfo)) {
            return;
        }
        Iterables.removeIf(((TaskInfo) workInfo).getShouldSuccessors(), new Predicate<WorkInfo>() {
            @Override
            @SuppressWarnings("NullableProblems")
            public boolean apply(WorkInfo input) {
                return visitingNodes.containsEntry(input, taskNodeWithVisitingSegment.visitingSegment);
            }
        });
    }

    private void takePlanSnapshotIfCanBeRestoredToCurrentTask(Map<WorkInfo, Integer> planBeforeVisiting, WorkInfo workInfo) {
        if (workInfo instanceof TaskInfo && !((TaskInfo) workInfo).getShouldSuccessors().isEmpty()) {
            planBeforeVisiting.put(workInfo, workInfoMapping.size());
        }
    }

    private void recordEdgeIfArrivedViaShouldRunAfter(Deque<GraphEdge> walkedShouldRunAfterEdges, Deque<WorkInfo> path, WorkInfo workInfo) {
        if (!(workInfo instanceof TaskInfo)) {
            return;
        }
        WorkInfo previous = path.peek();
        if (previous instanceof TaskInfo && ((TaskInfo) previous).getShouldSuccessors().contains(workInfo)) {
            walkedShouldRunAfterEdges.push(new GraphEdge(previous, workInfo));
        }
    }

    /**
     * Given a finalizer task, determine where in the current node queue that it should be inserted.
     * The finalizer should be inserted after any of it's preceding tasks.
     */
    private int finalizerTaskPosition(WorkInfo finalizer, final List<WorkInfoInVisitingSegment> nodeQueue) {
        if (nodeQueue.size() == 0) {
            return 0;
        }

        Set<WorkInfo> precedingTasks = getAllPrecedingTasks(finalizer);
        Set<Integer> precedingTaskIndices = CollectionUtils.collect(precedingTasks, new Transformer<Integer, WorkInfo>() {
            @Override
            public Integer transform(final WorkInfo dependsOnTask) {
                return Iterables.indexOf(nodeQueue, new Predicate<WorkInfoInVisitingSegment>() {
                    @Override
                    @SuppressWarnings("NullableProblems")
                    public boolean apply(WorkInfoInVisitingSegment taskInfoInVisitingSegment) {
                        return taskInfoInVisitingSegment.workInfo.equals(dependsOnTask);
                    }
                });
            }
        });
        return Collections.max(precedingTaskIndices) + 1;
    }

    private Set<WorkInfo> getAllPrecedingTasks(WorkInfo finalizer) {
        Set<WorkInfo> precedingTasks = Sets.newHashSet();
        Deque<WorkInfo> candidateTasks = new ArrayDeque<WorkInfo>();

        // Consider every task that must run before the finalizer
        Iterables.addAll(candidateTasks, finalizer.getAllSuccessors());

        // For each candidate task, add it to the preceding tasks.
        while (!candidateTasks.isEmpty()) {
            WorkInfo precedingTask = candidateTasks.pop();
            if (precedingTasks.add(precedingTask) && precedingTask instanceof TaskInfo) {
                // Any task that the preceding task must run after is also a preceding task.
                candidateTasks.addAll(((TaskInfo) precedingTask).getMustSuccessors());
                candidateTasks.addAll(((TaskInfo) precedingTask).getFinalizingSuccessors());
            }
        }

        return precedingTasks;
    }

    private void onOrderingCycle(WorkInfo successor, WorkInfo workInfo) {
        CachingDirectedGraphWalker<WorkInfo, Void> graphWalker = new CachingDirectedGraphWalker<WorkInfo, Void>(new DirectedGraph<WorkInfo, Void>() {
            @Override
            public void getNodeValues(WorkInfo node, Collection<? super Void> values, Collection<? super WorkInfo> connectedNodes) {
                connectedNodes.addAll(node.getDependencySuccessors());
                if (node instanceof TaskInfo) {
                    TaskInfo taskInfo = (TaskInfo) node;
                    connectedNodes.addAll(taskInfo.getMustSuccessors());
                    connectedNodes.addAll(taskInfo.getFinalizingSuccessors());
                }
            }
        });
        graphWalker.add(entryTasks);

        List<Set<WorkInfo>> cycles = graphWalker.findCycles();
        if (cycles.isEmpty()) {
            // TODO: This isn't correct. This means that we've detected a cycle while determining the execution plan, but the graph walker did not find one.
            // https://github.com/gradle/gradle/issues/2293
            throw new GradleException("Misdetected cycle between " + workInfo + " and " + successor + ". Help us by reporting this to https://github.com/gradle/gradle/issues/2293");
        }
        final List<WorkInfo> firstCycle = new ArrayList<WorkInfo>(cycles.get(0));
        Collections.sort(firstCycle);

        DirectedGraphRenderer<WorkInfo> graphRenderer = new DirectedGraphRenderer<WorkInfo>(new GraphNodeRenderer<WorkInfo>() {
            @Override
            public void renderTo(WorkInfo node, StyledTextOutput output) {
                output.withStyle(StyledTextOutput.Style.Identifier).text(node);
            }
        }, new DirectedGraph<WorkInfo, Object>() {
            @Override
            public void getNodeValues(WorkInfo node, Collection<? super Object> values, Collection<? super WorkInfo> connectedNodes) {
                for (WorkInfo dependency : firstCycle) {
                    if (node.hasHardSuccessor(dependency)) {
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
        dependencyResolver.clear();
        entryTasks.clear();
        workInfoMapping.clear();
        executionQueue.clear();
        projectLocks.clear();
        failureCollector.clearFailures();
        workMutations.clear();
        canonicalizedFileCache.clear();
        reachableCache.clear();
        dependenciesCompleteCache.clear();
        runningNodes.clear();
    }

    @Override
    public Set<Task> getTasks() {
        return workInfoMapping.getTasks();
    }

    @Override
    public Set<Task> getFilteredTasks() {
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (WorkInfo filteredNode : filteredNodes) {
            if (filteredNode instanceof LocalTaskInfo) {
                builder.add(((LocalTaskInfo) filteredNode).getTask());
            }
        }
        return builder.build();
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public void setContinueOnFailure(boolean continueOnFailre) {
        this.continueOnFailure = continueOnFailre;
    }

    @Override
    @Nullable
    public WorkInfo selectNext(WorkerLeaseRegistry.WorkerLease workerLease, ResourceLockState resourceLockState) {
        if (allProjectsLocked()) {
            return null;
        }

        Iterator<WorkInfo> iterator = executionQueue.iterator();
        while (iterator.hasNext()) {
            WorkInfo workInfo = iterator.next();
            if (workInfo.isReady() && allDependenciesComplete(workInfo)) {
                MutationInfo mutations = getResolvedMutationInfo(workInfo);

                // TODO: convert output file checks to a resource lock
                if (!tryLockProjectFor(workInfo)
                    || !workerLease.tryLock()
                    || !canRunWithCurrentlyExecutedTasks(workInfo, mutations)) {
                    resourceLockState.releaseLocks();
                    continue;
                }

                if (workInfo.allDependenciesSuccessful()) {
                    recordWorkStarted(workInfo);
                    workInfo.startExecution();
                } else {
                    workInfo.skipExecution();
                }
                iterator.remove();

                return workInfo;
            }
        }
        return null;
    }

    private boolean tryLockProjectFor(WorkInfo workInfo) {
        if (workInfo instanceof LocalTaskInfo) {
            return getProjectLock((LocalTaskInfo) workInfo).tryLock();
        } else {
            return true;
        }
    }

    private void unlockProjectFor(WorkInfo workInfo) {
        if (workInfo instanceof LocalTaskInfo) {
            getProjectLock((LocalTaskInfo) workInfo).unlock();
        }
    }

    private ResourceLock getProjectLock(LocalTaskInfo taskInfo) {
        return projectLocks.get(taskInfo.getTask().getProject());
    }

    private MutationInfo getResolvedMutationInfo(WorkInfo workInfo) {
        MutationInfo mutations = workMutations.get(workInfo);
        if (!mutations.resolved) {
            resolveMutations(mutations, workInfo);
        }
        return mutations;
    }

    private void resolveMutations(MutationInfo mutations, WorkInfo workInfo) {
        if (workInfo instanceof LocalTaskInfo) {
            LocalTaskInfo taskInfo = (LocalTaskInfo) workInfo;
            TaskInternal task = taskInfo.getTask();
            ProjectInternal project = (ProjectInternal) task.getProject();
            ServiceRegistry serviceRegistry = project.getServices();
            PathToFileResolver resolver = serviceRegistry.get(PathToFileResolver.class);
            PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);
            TaskProperties taskProperties = DefaultTaskProperties.resolve(propertyWalker, resolver, task);
            mutations.outputPaths.addAll(getOutputPaths(canonicalizedFileCache, taskInfo, taskProperties.getOutputFiles(), taskProperties.getLocalStateFiles()));
            mutations.destroyablePaths.addAll(getDestroyablePaths(canonicalizedFileCache, taskInfo, taskProperties.getDestroyableFiles()));
            mutations.hasFileInputs = !taskProperties.getInputFileProperties().isEmpty();
            mutations.hasOutputs = taskProperties.hasDeclaredOutputs();
            mutations.hasLocalState = !taskProperties.getLocalStateFiles().isEmpty();
            mutations.resolved = true;

            if (!mutations.destroyablePaths.isEmpty()) {
                if (mutations.hasOutputs) {
                    throw new IllegalStateException("Task " + taskInfo + " has both outputs and destroyables defined.  A task can define either outputs or destroyables, but not both.");
                }
                if (mutations.hasFileInputs) {
                    throw new IllegalStateException("Task " + taskInfo + " has both inputs and destroyables defined.  A task can define either inputs or destroyables, but not both.");
                }
                if (mutations.hasLocalState) {
                    throw new IllegalStateException("Task " + taskInfo + " has both local state and destroyables defined.  A task can define either local state or destroyables, but not both.");
                }
            }
        }
    }

    private boolean allDependenciesComplete(WorkInfo workInfo) {
        if (dependenciesCompleteCache.contains(workInfo)) {
            return true;
        }

        boolean dependenciesComplete = workInfo.allDependenciesComplete();
        if (dependenciesComplete) {
            dependenciesCompleteCache.add(workInfo);
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

    private ResourceLock getOrCreateProjectLock(Project project) {
        String gradlePath = ((GradleInternal) project.getGradle()).getIdentityPath().toString();
        String projectPath = ((ProjectInternal) project).getIdentityPath().toString();
        return workerLeaseService.getProjectLock(gradlePath, projectPath);
    }

    private boolean canRunWithCurrentlyExecutedTasks(WorkInfo taskInfo, MutationInfo mutations) {
        Set<String> candidateTaskDestroyables = mutations.destroyablePaths;

        if (!runningNodes.isEmpty()) {
            Set<String> candidateTaskOutputs = mutations.outputPaths;
            Set<String> candidateMutations = !candidateTaskOutputs.isEmpty() ? candidateTaskOutputs : candidateTaskDestroyables;
            if (hasTaskWithOverlappingMutations(candidateMutations)) {
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

    private boolean hasTaskWithOverlappingMutations(Set<String> candidateMutationPaths) {
        if (!candidateMutationPaths.isEmpty()) {
            for (WorkInfo runningWork : runningNodes) {
                MutationInfo runningMutations = workMutations.get(runningWork);
                Iterable<String> runningMutationPaths = Iterables.concat(runningMutations.outputPaths, runningMutations.destroyablePaths);
                if (hasOverlap(candidateMutationPaths, runningMutationPaths)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean doesDestroyNotYetConsumedOutputOfAnotherTask(WorkInfo destroyerTask, Set<String> destroyablePaths) {
        if (!destroyablePaths.isEmpty()) {
            for (MutationInfo producingWork : workMutations.values()) {
                if (!producingWork.workInfo.isComplete()) {
                    // We don't care about producing tasks that haven't finished yet
                    continue;
                }
                if (producingWork.consumingWork.isEmpty()) {
                    // We don't care about tasks whose output is not consumed by anyone anymore
                    continue;
                }
                if (!hasOverlap(destroyablePaths, producingWork.outputPaths)) {
                    // No overlap no cry
                    continue;
                }
                for (WorkInfo consumer : producingWork.consumingWork) {
                    if (doesConsumerDependOnDestroyer(consumer, destroyerTask)) {
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

    private boolean doesConsumerDependOnDestroyer(WorkInfo consumer, WorkInfo destroyer) {
        if (consumer == destroyer) {
            return true;
        }
        Pair<WorkInfo, WorkInfo> workPair = Pair.of(consumer, destroyer);
        if (reachableCache.get(workPair) != null) {
            return reachableCache.get(workPair);
        }

        boolean reachable = false;
        for (WorkInfo dependency : consumer.getAllSuccessors()) {
            if (!dependency.isComplete()) {
                if (doesConsumerDependOnDestroyer(dependency, destroyer)) {
                    reachable = true;
                }
            }
        }

        reachableCache.put(workPair, reachable);
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

    private void recordWorkStarted(WorkInfo workInfo) {
        runningNodes.add(workInfo);
    }

    private void recordWorkCompleted(WorkInfo workInfo) {
        runningNodes.remove(workInfo);
        MutationInfo mutations = workMutations.get(workInfo);
        for (WorkInfo producer : mutations.consumesOutputOf) {
            MutationInfo producerMutations = workMutations.get(producer);
            if (producerMutations.consumingWork.remove(workInfo) && canRemoveMutation(producerMutations)) {
                workMutations.remove(producer);
            }
        }

        if (canRemoveMutation(mutations)) {
            workMutations.remove(workInfo);
        }
    }

    private static boolean canRemoveMutation(@Nullable MutationInfo mutations) {
        return mutations != null && mutations.workInfo.isComplete() && mutations.consumingWork.isEmpty();
    }

    @Override
    public void workComplete(WorkInfo workInfo) {
        try {
            if (!workInfo.isComplete()) {
                enforceFinalizerTasks(workInfo);
                if (workInfo.isFailed()) {
                    handleFailure(workInfo);
                }

                workInfo.finishExecution();
                recordWorkCompleted(workInfo);
            }
        } finally {
            unlockProjectFor(workInfo);
        }
    }

    private static void enforceFinalizerTasks(WorkInfo workInfo) {
        if (!(workInfo instanceof TaskInfo)) {
            return;
        }
        for (WorkInfo finalizerNode : ((TaskInfo) workInfo).getFinalizers()) {
            if (finalizerNode.isRequired() || finalizerNode.isMustNotRun()) {
                enforceWithDependencies(finalizerNode, Sets.<WorkInfo>newHashSet());
            }
        }
    }

    private static void enforceWithDependencies(WorkInfo nodeInfo, Set<WorkInfo> enforcedNodes) {
        Deque<WorkInfo> candidateNodes = new ArrayDeque<WorkInfo>();
        candidateNodes.add(nodeInfo);

        while (!candidateNodes.isEmpty()) {
            WorkInfo node = candidateNodes.pop();
            if (!enforcedNodes.contains(node)) {
                enforcedNodes.add(node);

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

    private void handleFailure(WorkInfo workInfo) {
        Throwable executionFailure = workInfo.getExecutionFailure();
        if (executionFailure != null) {
            // Always abort execution for an execution failure (as opposed to a work failure)
            abortExecution();
            this.failureCollector.addFailure(executionFailure);
            return;
        }

        // Work failure
        try {
            if (!continueOnFailure) {
                workInfo.rethrowFailure();
            }
            this.failureCollector.addFailure(workInfo.getWorkFailure());
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
        for (WorkInfo workInfo : workInfoMapping) {
            // Allow currently executing and enforced tasks to complete, but skip everything else.
            if (workInfo.isRequired()) {
                workInfo.skipExecution();
                aborted = true;
            }

            // If abortAll is set, also stop enforced tasks.
            if (abortAll && workInfo.isReady()) {
                workInfo.abortExecution();
                aborted = true;
            }
        }
        return aborted;
    }

    @Override
    public void collectFailures(Collection<? super Throwable> failures) {
        if (tasksCancelled) {
            failures.add(new BuildCancelledException());
        }
        failures.addAll(failureCollector.getFailures());
    }

    @Override
    public boolean allTasksComplete() {
        for (WorkInfo workInfo : workInfoMapping) {
            if (!workInfo.isComplete()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasWorkRemaining() {
        for (WorkInfo workInfo : executionQueue) {
            if (!workInfo.isComplete()) {
                return true;
            }
        }
        return !runningNodes.isEmpty();
    }

    @Override
    public int size() {
        return workInfoMapping.workInfos.size();
    }

    private static class GraphEdge {
        private final WorkInfo from;
        private final WorkInfo to;

        private GraphEdge(WorkInfo from, WorkInfo to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class WorkInfoInVisitingSegment {
        private final WorkInfo workInfo;
        private final int visitingSegment;

        private WorkInfoInVisitingSegment(WorkInfo workInfo, int visitingSegment) {
            this.workInfo = workInfo;
            this.visitingSegment = visitingSegment;
        }
    }

    private static class MutationInfo {
        final WorkInfo workInfo;
        final Set<WorkInfo> consumingWork = Sets.newHashSet();
        final Set<WorkInfo> consumesOutputOf = Sets.newHashSet();
        final Set<String> outputPaths = Sets.newHashSet();
        final Set<String> destroyablePaths = Sets.newHashSet();
        boolean hasFileInputs;
        boolean hasOutputs;
        boolean hasLocalState;
        boolean resolved;

        MutationInfo(WorkInfo workInfo) {
            this.workInfo = workInfo;
        }
    }

    private static class WorkInfoMapping extends AbstractCollection<WorkInfo> {
        private final Map<Task, LocalTaskInfo> taskMapping = Maps.newLinkedHashMap();
        private final Set<WorkInfo> workInfos = Sets.newLinkedHashSet();

        @Override
        public boolean contains(Object o) {
            return workInfos.contains(o);
        }

        @Override
        public boolean add(WorkInfo workInfo) {
            if (!workInfos.add(workInfo)) {
                return false;
            }
            if (workInfo instanceof LocalTaskInfo) {
                LocalTaskInfo taskInfo = (LocalTaskInfo) workInfo;
                taskMapping.put(taskInfo.getTask(), taskInfo);
            }
            return true;
        }

        public TaskInfo get(Task task) {
            TaskInfo taskInfo = taskMapping.get(task);
            if (taskInfo == null) {
                throw new IllegalStateException("Task is not part of the execution plan, no dependency information is available.");
            }
            return taskInfo;
        }

        public Set<Task> getTasks() {
            return taskMapping.keySet();
        }

        @Override
        public Iterator<WorkInfo> iterator() {
            return workInfos.iterator();
        }

        @Override
        public void clear() {
            workInfos.clear();
            taskMapping.clear();
        }

        @Override
        public int size() {
            return workInfos.size();
        }

        public void retainFirst(int count) {
            Iterator<WorkInfo> executionPlanIterator = workInfos.iterator();
            for (int i = 0; i < count; i++) {
                executionPlanIterator.next();
            }
            while (executionPlanIterator.hasNext()) {
                WorkInfo removedWork = executionPlanIterator.next();
                executionPlanIterator.remove();
                if (removedWork instanceof LocalTaskInfo) {
                    taskMapping.remove(((LocalTaskInfo) removedWork).getTask());
                }
            }
        }
    }
}
