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

package org.gradle.execution.plan;

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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.Pair;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A reusable implementation of ExecutionPlan. The {@link #addEntryTasks(java.util.Collection)} and {@link #clear()} methods are NOT threadsafe, and callers must synchronize access to these methods.
 */
@NonNullApi
public class DefaultExecutionPlan implements ExecutionPlan {
    private final Set<TaskNode> entryTasks = new LinkedHashSet<TaskNode>();
    private final NodeMapping nodeMapping = new NodeMapping();
    private final List<Node> executionQueue = Lists.newLinkedList();
    private final Map<Project, ResourceLock> projectLocks = Maps.newHashMap();
    private final FailureCollector failureCollector = new FailureCollector();
    private final TaskNodeFactory taskNodeFactory;
    private final TaskDependencyResolver dependencyResolver;
    private Spec<? super Task> filter = Specs.satisfyAll();

    private boolean continueOnFailure;

    private final Set<Node> runningNodes = Sets.newIdentityHashSet();
    private final Set<Node> filteredNodes = Sets.newIdentityHashSet();
    private final Map<Node, MutationInfo> mutations = Maps.newIdentityHashMap();
    private final Map<File, String> canonicalizedFileCache = Maps.newIdentityHashMap();
    private final Map<Pair<Node, Node>, Boolean> reachableCache = Maps.newHashMap();
    private final Set<Node> dependenciesCompleteCache = Sets.newHashSet();
    private final Set<Node> dependenciesWithChanges = Sets.newHashSet();
    private final Set<Node> dependenciesWhichRequireMonitoring = Sets.newHashSet();
    private final Set<Node> readyToExecute = Sets.newHashSet();
    private final WorkerLeaseService workerLeaseService;
    private final GradleInternal gradle;

    private boolean buildCancelled;

    public DefaultExecutionPlan(WorkerLeaseService workerLeaseService, GradleInternal gradle, TaskNodeFactory taskNodeFactory, TaskDependencyResolver dependencyResolver) {
        this.workerLeaseService = workerLeaseService;
        this.gradle = gradle;
        this.taskNodeFactory = taskNodeFactory;
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

    @Override
    public TaskNode getNode(Task task) {
        return nodeMapping.get(task);
    }

    public void addEntryTasks(Collection<? extends Task> tasks) {
        final Deque<Node> queue = new ArrayDeque<Node>();
        Set<Node> nodesInUnknownState = Sets.newLinkedHashSet();

        List<Task> sortedTasks = new ArrayList<Task>(tasks);
        Collections.sort(sortedTasks);
        for (Task task : sortedTasks) {
            TaskNode node = taskNodeFactory.getOrCreateNode(task);
            if (node.isMustNotRun()) {
                requireWithDependencies(node);
            } else if (filter.isSatisfiedBy(task)) {
                node.require();
            }
            entryTasks.add(node);
            queue.add(node);
        }

        final Set<Node> visiting = Sets.newHashSet();

        while (!queue.isEmpty()) {
            Node node = queue.getFirst();
            if (node.getDependenciesProcessed()) {
                // Have already visited this node - skip it
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
                // Have not seen this node before - add its dependencies to the head of the queue and leave this
                // node in the queue
                // Make sure it has been configured
                node.prepareForExecution();
                node.resolveDependencies(dependencyResolver, new Action<Node>() {
                    @Override
                    public void execute(Node targetNode) {
                        if (!visiting.contains(targetNode)) {
                            queue.addFirst(targetNode);
                        }
                    }
                });
                if (node.isRequired()) {
                    for (Node successor : node.getDependencySuccessors()) {
                        if (nodeSatisfiesTaskFilter(successor)) {
                            successor.require();
                        }
                    }
                } else {
                    nodesInUnknownState.add(node);
                }
            } else {
                // Have visited this node's dependencies - add it to the graph
                queue.removeFirst();
                visiting.remove(node);
                node.dependenciesProcessed();
            }
        }
        resolveNodesInUnknownState(nodesInUnknownState);
    }

    private boolean nodeSatisfiesTaskFilter(Node successor) {
        if (successor instanceof LocalTaskNode) {
            return filter.isSatisfiedBy(((LocalTaskNode) successor).getTask());
        }
        return true;
    }

    private void resolveNodesInUnknownState(Set<Node> nodesInUnknownState) {
        List<Node> queue = Lists.newArrayList(nodesInUnknownState);
        Set<Node> visiting = Sets.newHashSet();

        while (!queue.isEmpty()) {
            Node node = queue.get(0);
            if (node.isInKnownState()) {
                queue.remove(0);
                continue;
            }

            if (visiting.add(node)) {
                for (Node hardPredecessor : node.getDependencyPredecessors()) {
                    if (!visiting.contains(hardPredecessor)) {
                        queue.add(0, hardPredecessor);
                    }
                }
            } else {
                queue.remove(0);
                visiting.remove(node);
                node.mustNotRun();
                for (Node predecessor : node.getDependencyPredecessors()) {
                    assert predecessor.isRequired() || predecessor.isMustNotRun();
                    if (predecessor.isRequired()) {
                        node.require();
                        break;
                    }
                }
            }
        }
    }

    private void requireWithDependencies(Node node) {
        if (node.isMustNotRun() && nodeSatisfiesTaskFilter(node)) {
            node.require();
            for (Node dependency : node.getDependencySuccessors()) {
                requireWithDependencies(dependency);
            }
        }
    }

    public void determineExecutionPlan() {
        List<NodeInVisitingSegment> nodeQueue = Lists.newArrayList(Iterables.transform(entryTasks, new Function<TaskNode, NodeInVisitingSegment>() {
            private int index;

            @Override
            @SuppressWarnings("NullableProblems")
            public NodeInVisitingSegment apply(TaskNode taskNode) {
                return new NodeInVisitingSegment(taskNode, index++);
            }
        }));
        int visitingSegmentCounter = nodeQueue.size();

        HashMultimap<Node, Integer> visitingNodes = HashMultimap.create();
        Deque<GraphEdge> walkedShouldRunAfterEdges = new ArrayDeque<GraphEdge>();
        Deque<Node> path = new ArrayDeque<Node>();
        Map<Node, Integer> planBeforeVisiting = Maps.newHashMap();

        while (!nodeQueue.isEmpty()) {
            NodeInVisitingSegment nodeInVisitingSegment = nodeQueue.get(0);
            int currentSegment = nodeInVisitingSegment.visitingSegment;
            Node node = nodeInVisitingSegment.node;

            if (!node.isIncludeInGraph() || nodeMapping.contains(node)) {
                nodeQueue.remove(0);
                visitingNodes.remove(node, currentSegment);
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, node);
                if (node.requiresMonitoring()) {
                    dependenciesWhichRequireMonitoring.add(node);
                }
                continue;
            }

            boolean alreadyVisited = visitingNodes.containsKey(node);
            visitingNodes.put(node, currentSegment);

            if (!alreadyVisited) {
                // Have not seen this node before - add its dependencies to the head of the queue and leave this
                // node in the queue
                recordEdgeIfArrivedViaShouldRunAfter(walkedShouldRunAfterEdges, path, node);
                removeShouldRunAfterSuccessorsIfTheyImposeACycle(visitingNodes, nodeInVisitingSegment);
                takePlanSnapshotIfCanBeRestoredToCurrentTask(planBeforeVisiting, node);

                for (Node successor : node.getAllSuccessorsInReverseOrder()) {
                    if (visitingNodes.containsEntry(successor, currentSegment)) {
                        if (!walkedShouldRunAfterEdges.isEmpty()) {
                            //remove the last walked should run after edge and restore state from before walking it
                            GraphEdge toBeRemoved = walkedShouldRunAfterEdges.pop();
                            // Should run after edges only exist between tasks, so this cast is safe
                            TaskNode sourceTask = (TaskNode) toBeRemoved.from;
                            TaskNode targetTask = (TaskNode) toBeRemoved.to;
                            sourceTask.removeShouldSuccessor(targetTask);
                            restorePath(path, toBeRemoved);
                            restoreQueue(nodeQueue, visitingNodes, toBeRemoved);
                            restoreExecutionPlan(planBeforeVisiting, toBeRemoved);
                            break;
                        } else {
                            onOrderingCycle(successor, node);
                        }
                    }
                    nodeQueue.add(0, new NodeInVisitingSegment(successor, currentSegment));
                }
                path.push(node);
            } else {
                // Have visited this node's dependencies - add it to the end of the plan
                nodeQueue.remove(0);
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, node);
                visitingNodes.remove(node, currentSegment);
                path.pop();
                nodeMapping.add(node);
                if (node.requiresMonitoring()) {
                    dependenciesWhichRequireMonitoring.add(node);
                }

                MutationInfo mutations = getOrCreateMutationsOf(node);
                for (Node dependency : node.getDependencySuccessors()) {
                    getOrCreateMutationsOf(dependency).consumingNodes.add(node);
                    mutations.producingNodes.add(dependency);
                }

                Project project = node.getProject();
                if (project != null) {
                    projectLocks.put(project, getOrCreateProjectLock(project));
                }

                // Add any finalizers to the queue
                for (Node finalizer : node.getFinalizers()) {
                    if (!visitingNodes.containsKey(finalizer)) {
                        nodeQueue.add(finalizerTaskPosition(finalizer, nodeQueue), new NodeInVisitingSegment(finalizer, visitingSegmentCounter++));
                    }
                }
            }
        }
        executionQueue.clear();
        Iterables.addAll(executionQueue, nodeMapping);
        Iterables.addAll(dependenciesWithChanges, nodeMapping);
    }

    private MutationInfo getOrCreateMutationsOf(Node node) {
        MutationInfo mutations = this.mutations.get(node);
        if (mutations == null) {
            mutations = new MutationInfo(node);
            this.mutations.put(node, mutations);
        }
        return mutations;
    }

    private void maybeRemoveProcessedShouldRunAfterEdge(Deque<GraphEdge> walkedShouldRunAfterEdges, Node node) {
        GraphEdge edge = walkedShouldRunAfterEdges.peek();
        if (edge != null && edge.to.equals(node)) {
            walkedShouldRunAfterEdges.pop();
        }
    }

    private void restoreExecutionPlan(Map<Node, Integer> planBeforeVisiting, GraphEdge toBeRemoved) {
        int count = planBeforeVisiting.get(toBeRemoved.from);
        nodeMapping.retainFirst(count);
    }

    private void restoreQueue(List<NodeInVisitingSegment> nodeQueue, HashMultimap<Node, Integer> visitingNodes, GraphEdge toBeRemoved) {
        NodeInVisitingSegment nextInQueue = null;
        while (nextInQueue == null || !toBeRemoved.from.equals(nextInQueue.node)) {
            nextInQueue = nodeQueue.get(0);
            visitingNodes.remove(nextInQueue.node, nextInQueue.visitingSegment);
            if (!toBeRemoved.from.equals(nextInQueue.node)) {
                nodeQueue.remove(0);
            }
        }
    }

    private void restorePath(Deque<Node> path, GraphEdge toBeRemoved) {
        Node removedFromPath = null;
        while (!toBeRemoved.from.equals(removedFromPath)) {
            removedFromPath = path.pop();
        }
    }

    private void removeShouldRunAfterSuccessorsIfTheyImposeACycle(final HashMultimap<Node, Integer> visitingNodes, final NodeInVisitingSegment nodeWithVisitingSegment) {
        Node node = nodeWithVisitingSegment.node;
        if (!(node instanceof TaskNode)) {
            return;
        }
        Iterables.removeIf(((TaskNode) node).getShouldSuccessors(), new Predicate<Node>() {
            @Override
            @SuppressWarnings("NullableProblems")
            public boolean apply(Node input) {
                return visitingNodes.containsEntry(input, nodeWithVisitingSegment.visitingSegment);
            }
        });
    }

    private void takePlanSnapshotIfCanBeRestoredToCurrentTask(Map<Node, Integer> planBeforeVisiting, Node node) {
        if (node instanceof TaskNode && !((TaskNode) node).getShouldSuccessors().isEmpty()) {
            planBeforeVisiting.put(node, nodeMapping.size());
        }
    }

    private void recordEdgeIfArrivedViaShouldRunAfter(Deque<GraphEdge> walkedShouldRunAfterEdges, Deque<Node> path, Node node) {
        if (!(node instanceof TaskNode)) {
            return;
        }
        Node previous = path.peek();
        if (previous instanceof TaskNode && ((TaskNode) previous).getShouldSuccessors().contains(node)) {
            walkedShouldRunAfterEdges.push(new GraphEdge(previous, node));
        }
    }

    /**
     * Given a finalizer task, determine where in the current node queue that it should be inserted.
     * The finalizer should be inserted after any of it's preceding tasks.
     */
    private int finalizerTaskPosition(Node finalizer, final List<NodeInVisitingSegment> nodeQueue) {
        if (nodeQueue.size() == 0) {
            return 0;
        }

        Set<Node> precedingTasks = getAllPrecedingNodes(finalizer);
        Set<Integer> precedingTaskIndices = CollectionUtils.collect(precedingTasks, new Transformer<Integer, Node>() {
            @Override
            public Integer transform(final Node dependsOnTask) {
                return Iterables.indexOf(nodeQueue, new Predicate<NodeInVisitingSegment>() {
                    @Override
                    @SuppressWarnings("NullableProblems")
                    public boolean apply(NodeInVisitingSegment nodeInVisitingSegment) {
                        return nodeInVisitingSegment.node.equals(dependsOnTask);
                    }
                });
            }
        });
        return Collections.max(precedingTaskIndices) + 1;
    }

    private Set<Node> getAllPrecedingNodes(Node finalizer) {
        Set<Node> precedingNodes = Sets.newHashSet();
        Deque<Node> candidateNodes = new ArrayDeque<Node>();

        // Consider every node that must run before the finalizer
        Iterables.addAll(candidateNodes, finalizer.getAllSuccessors());

        // For each candidate node, add it to the preceding nodes.
        while (!candidateNodes.isEmpty()) {
            Node precedingNode = candidateNodes.pop();
            if (precedingNodes.add(precedingNode) && precedingNode instanceof TaskNode) {
                // Any node that the preceding task must run after is also a preceding node.
                candidateNodes.addAll(((TaskNode) precedingNode).getMustSuccessors());
                candidateNodes.addAll(((TaskNode) precedingNode).getFinalizingSuccessors());
            }
        }

        return precedingNodes;
    }

    private void onOrderingCycle(Node successor, Node node) {
        CachingDirectedGraphWalker<Node, Void> graphWalker = new CachingDirectedGraphWalker<Node, Void>(new DirectedGraph<Node, Void>() {
            @Override
            public void getNodeValues(Node node, Collection<? super Void> values, Collection<? super Node> connectedNodes) {
                connectedNodes.addAll(node.getDependencySuccessors());
                if (node instanceof TaskNode) {
                    TaskNode taskNode = (TaskNode) node;
                    connectedNodes.addAll(taskNode.getMustSuccessors());
                    connectedNodes.addAll(taskNode.getFinalizingSuccessors());
                }
            }
        });
        graphWalker.add(entryTasks);

        List<Set<Node>> cycles = graphWalker.findCycles();
        if (cycles.isEmpty()) {
            // TODO: This isn't correct. This means that we've detected a cycle while determining the execution plan, but the graph walker did not find one.
            // https://github.com/gradle/gradle/issues/2293
            throw new GradleException("Misdetected cycle between " + node + " and " + successor + ". Help us by reporting this to https://github.com/gradle/gradle/issues/2293");
        }
        final List<Node> firstCycle = new ArrayList<Node>(cycles.get(0));
        Collections.sort(firstCycle);

        DirectedGraphRenderer<Node> graphRenderer = new DirectedGraphRenderer<Node>(new GraphNodeRenderer<Node>() {
            @Override
            public void renderTo(Node node, StyledTextOutput output) {
                output.withStyle(StyledTextOutput.Style.Identifier).text(node);
            }
        }, new DirectedGraph<Node, Object>() {
            @Override
            public void getNodeValues(Node node, Collection<? super Object> values, Collection<? super Node> connectedNodes) {
                for (Node dependency : firstCycle) {
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
        taskNodeFactory.clear();
        dependencyResolver.clear();
        entryTasks.clear();
        nodeMapping.clear();
        executionQueue.clear();
        projectLocks.clear();
        failureCollector.clearFailures();
        mutations.clear();
        canonicalizedFileCache.clear();
        reachableCache.clear();
        dependenciesCompleteCache.clear();
        dependenciesWithChanges.clear();
        dependenciesWhichRequireMonitoring.clear();
        runningNodes.clear();
    }

    @Override
    public Set<Task> getTasks() {
        return nodeMapping.getTasks();
    }

    @Override
    public Set<Task> getFilteredTasks() {
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (Node filteredNode : filteredNodes) {
            if (filteredNode instanceof LocalTaskNode) {
                builder.add(((LocalTaskNode) filteredNode).getTask());
            }
        }
        return builder.build();
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public void setContinueOnFailure(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
    }

    @Override
    @Nullable
    public Node selectNext(WorkerLeaseRegistry.WorkerLease workerLease, ResourceLockState resourceLockState) {
        if (allProjectsLocked()) {
            return null;
        }

        for (Node node : dependenciesWhichRequireMonitoring) {
            if (dependenciesCompleteCache.contains(node)) {
                continue;
            }
            if (node.isComplete()) {
                dependenciesCompleteCache.add(node);
                Iterables.addAll(dependenciesWithChanges, node.getAllPredecessors());
            }
        }
        if (readyToExecute.isEmpty() && dependenciesWithChanges.isEmpty()) {
            return null;
        }
        Iterator<Node> iterator = executionQueue.iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node.isReady() && allDependenciesComplete(node)) {
                MutationInfo mutations = getResolvedMutationInfo(node);

                // TODO: convert output file checks to a resource lock
                if (!tryLockProjectFor(node)
                    || !workerLease.tryLock()
                    || !canRunWithCurrentlyExecutedNodes(node, mutations)) {
                    resourceLockState.releaseLocks();
                    readyToExecute.add(node);
                    continue;
                }

                if (node.allDependenciesSuccessful()) {
                    recordNodeStarted(node);
                    node.startExecution();
                } else {
                    Iterables.addAll(dependenciesWithChanges, node.getAllPredecessors());
                    node.skipExecution();
                }
                iterator.remove();
                readyToExecute.remove(node);
                return node;
            }
        }
        return null;
    }

    private boolean tryLockProjectFor(Node node) {
        if (node.getProject() != null) {
            return getProjectLock(node.getProject()).tryLock();
        } else {
            return true;
        }
    }

    private void unlockProjectFor(Node node) {
        if (node.getProject() != null) {
            getProjectLock(node.getProject()).unlock();
        }
    }

    private ResourceLock getProjectLock(Project project) {
        return projectLocks.get(project);
    }

    private MutationInfo getResolvedMutationInfo(Node node) {
        MutationInfo mutations = this.mutations.get(node);
        if (!mutations.resolved) {
            resolveMutations(mutations, node);
        }
        return mutations;
    }

    private void resolveMutations(final MutationInfo mutations, Node node) {
        if (node instanceof LocalTaskNode) {
            final LocalTaskNode taskNode = (LocalTaskNode) node;
            final TaskInternal task = taskNode.getTask();
            ProjectInternal project = (ProjectInternal) task.getProject();
            ServiceRegistry serviceRegistry = project.getServices();
            final FileResolver resolver = serviceRegistry.get(FileResolver.class);
            final FileCollectionFactory fileCollectionFactory = serviceRegistry.get(FileCollectionFactory.class);
            PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);
            try {
                TaskPropertyUtils.visitProperties(propertyWalker, task, new PropertyVisitor.Adapter() {
                    @Override
                    public void visitOutputFileProperty(final String propertyName, boolean optional, final PropertyValue value, final OutputFilePropertyType filePropertyType) {
                        withDeadlockHandling(
                            taskNode,
                            "an output",
                            "output property '" + propertyName + "'",
                            new Runnable() {
                                @Override
                                public void run() {
                                    FileParameterUtils.resolveOutputFilePropertySpecs(task.toString(), propertyName, value, filePropertyType, fileCollectionFactory, new Consumer<OutputFilePropertySpec>() {
                                        @Override
                                        public void accept(OutputFilePropertySpec outputFilePropertySpec) {
                                            mutations.outputPaths.addAll(canonicalizedPaths(canonicalizedFileCache, outputFilePropertySpec.getPropertyFiles()));
                                        }
                                    });
                                }
                            }
                        );
                        mutations.hasOutputs = true;
                    }

                    @Override
                    public void visitLocalStateProperty(final Object value) {
                        withDeadlockHandling(taskNode, "a local state property", "local state properties", new Runnable() {
                            @Override
                            public void run() {
                                mutations.outputPaths.addAll(canonicalizedPaths(canonicalizedFileCache, resolver.resolveFiles(value)));
                            }
                        });
                        mutations.hasLocalState = true;
                    }

                    @Override
                    public void visitDestroyableProperty(final Object value) {
                        withDeadlockHandling(taskNode, "a destroyable", "destroyables", new Runnable() {
                            @Override
                            public void run() {
                                mutations.destroyablePaths.addAll(canonicalizedPaths(canonicalizedFileCache, resolver.resolveFiles(value)));
                            }
                        });
                    }

                    @Override
                    public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                        mutations.hasFileInputs = true;
                    }
                });
            } catch (Exception e) {
                throw new TaskExecutionException(task, e);
            }

            mutations.resolved = true;

            if (!mutations.destroyablePaths.isEmpty()) {
                if (mutations.hasOutputs) {
                    throw new IllegalStateException("Task " + taskNode + " has both outputs and destroyables defined.  A task can define either outputs or destroyables, but not both.");
                }
                if (mutations.hasFileInputs) {
                    throw new IllegalStateException("Task " + taskNode + " has both inputs and destroyables defined.  A task can define either inputs or destroyables, but not both.");
                }
                if (mutations.hasLocalState) {
                    throw new IllegalStateException("Task " + taskNode + " has both local state and destroyables defined.  A task can define either local state or destroyables, but not both.");
                }
            }
        }
    }

    private void withDeadlockHandling(TaskNode task, String singular, String description, Runnable runnable) {
        try {
            runnable.run();
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(String.format("A deadlock was detected while resolving the %s for task '%s'. This can be caused, for instance, by %s property causing dependency resolution.", description, task, singular), e);
        }
    }

    private boolean allDependenciesComplete(Node node) {
        if (dependenciesCompleteCache.contains(node)) {
            return true;
        }

        if (!dependenciesWithChanges.contains(node)) {
            return false;
        }
        boolean dependenciesComplete = node.allDependenciesComplete();
        dependenciesWithChanges.remove(node);
        if (dependenciesComplete) {
            dependenciesCompleteCache.add(node);
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
        Path buildPath = ((ProjectInternal) project).getMutationState().getOwner().getIdentityPath();
        Path projectPath = ((ProjectInternal) project).getIdentityPath();
        return workerLeaseService.getProjectLock(buildPath, projectPath);
    }

    private boolean canRunWithCurrentlyExecutedNodes(Node node, MutationInfo mutations) {
        Set<String> candidateNodeDestroyables = mutations.destroyablePaths;

        if (!runningNodes.isEmpty()) {
            Set<String> candidateNodeOutputs = mutations.outputPaths;
            Set<String> candidateMutations = !candidateNodeOutputs.isEmpty() ? candidateNodeOutputs : candidateNodeDestroyables;
            if (hasNodeWithOverlappingMutations(candidateMutations)) {
                return false;
            }
        }

        return !doesDestroyNotYetConsumedOutputOfAnotherNode(node, candidateNodeDestroyables);
    }

    private static ImmutableSet<String> canonicalizedPaths(final Map<File, String> cache, Iterable<File> files) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (File file : files) {
            builder.add(canonicalizePath(file, cache));
        }
        return builder.build();
    }

    private static String canonicalizePath(File file, Map<File, String> cache) {
        try {
            String path = cache.get(file);
            if (path == null) {
                path = file.getCanonicalPath();
                cache.put(file, path);
            }
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean hasNodeWithOverlappingMutations(Set<String> candidateMutationPaths) {
        if (!candidateMutationPaths.isEmpty()) {
            for (Node runningNode : runningNodes) {
                MutationInfo runningMutations = mutations.get(runningNode);
                Iterable<String> runningMutationPaths = Iterables.concat(runningMutations.outputPaths, runningMutations.destroyablePaths);
                if (hasOverlap(candidateMutationPaths, runningMutationPaths)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean doesDestroyNotYetConsumedOutputOfAnotherNode(Node destroyer, Set<String> destroyablePaths) {
        if (!destroyablePaths.isEmpty()) {
            for (MutationInfo producingNode : mutations.values()) {
                if (!producingNode.node.isComplete()) {
                    // We don't care about producing nodes that haven't finished yet
                    continue;
                }
                if (producingNode.consumingNodes.isEmpty()) {
                    // We don't care about nodes whose output is not consumed by anyone anymore
                    continue;
                }
                if (!hasOverlap(destroyablePaths, producingNode.outputPaths)) {
                    // No overlap no cry
                    continue;
                }
                for (Node consumer : producingNode.consumingNodes) {
                    if (doesConsumerDependOnDestroyer(consumer, destroyer)) {
                        // If there's an explicit dependency from consuming node to destroyer,
                        // then we accept that as the will of the user
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean doesConsumerDependOnDestroyer(Node consumer, Node destroyer) {
        if (consumer == destroyer) {
            return true;
        }
        Pair<Node, Node> nodePair = Pair.of(consumer, destroyer);
        if (reachableCache.get(nodePair) != null) {
            return reachableCache.get(nodePair);
        }

        boolean reachable = false;
        for (Node dependency : consumer.getAllSuccessors()) {
            if (!dependency.isComplete()) {
                if (doesConsumerDependOnDestroyer(dependency, destroyer)) {
                    reachable = true;
                }
            }
        }

        reachableCache.put(nodePair, reachable);
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

    private void recordNodeStarted(Node node) {
        runningNodes.add(node);
    }

    private void recordNodeCompleted(Node node) {
        runningNodes.remove(node);
        Iterables.addAll(dependenciesWithChanges, node.getAllPredecessors());
        MutationInfo mutations = this.mutations.get(node);
        for (Node producer : mutations.producingNodes) {
            MutationInfo producerMutations = this.mutations.get(producer);
            if (producerMutations.consumingNodes.remove(node) && canRemoveMutation(producerMutations)) {
                this.mutations.remove(producer);
            }
        }

        if (canRemoveMutation(mutations)) {
            this.mutations.remove(node);
        }
    }

    private static boolean canRemoveMutation(@Nullable MutationInfo mutations) {
        return mutations != null && mutations.node.isComplete() && mutations.consumingNodes.isEmpty();
    }

    @Override
    public void nodeComplete(Node node) {
        try {
            if (!node.isComplete()) {
                enforceFinalizers(node, dependenciesWithChanges);
                if (node.isFailed()) {
                    handleFailure(node);
                }

                node.finishExecution();
                recordNodeCompleted(node);
            }
        } finally {
            unlockProjectFor(node);
        }
    }

    private static void enforceFinalizers(Node node, Set<Node> nodesWithDependencyChanges) {
        for (Node finalizerNode : node.getFinalizers()) {
            nodesWithDependencyChanges.add(finalizerNode);
            if (finalizerNode.isRequired() || finalizerNode.isMustNotRun()) {
                HashSet<Node> enforcedNodes = Sets.newHashSet();
                enforceWithDependencies(finalizerNode, enforcedNodes);
                nodesWithDependencyChanges.addAll(enforcedNodes);
            }
        }
    }

    private static void enforceWithDependencies(Node nodeInfo, Set<Node> enforcedNodes) {
        Deque<Node> candidateNodes = new ArrayDeque<Node>();
        candidateNodes.add(nodeInfo);

        while (!candidateNodes.isEmpty()) {
            Node node = candidateNodes.pop();
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

    private void handleFailure(Node node) {
        Throwable executionFailure = node.getExecutionFailure();
        if (executionFailure != null) {
            // Always abort execution for an execution failure (as opposed to a node failure)
            abortExecution();
            this.failureCollector.addFailure(executionFailure);
            return;
        }

        // Failure
        try {
            if (!continueOnFailure) {
                node.rethrowNodeFailure();
            }
            this.failureCollector.addFailure(node.getNodeFailure());
        } catch (Exception e) {
            // If the failure handler rethrows exception, then execution of other nodes is aborted. (--continue will collect failures)
            abortExecution();
            this.failureCollector.addFailure(e);
        }
    }

    private boolean abortExecution() {
        return abortExecution(false);
    }

    @Override
    public void cancelExecution() {
        buildCancelled = abortExecution() || buildCancelled;
    }

    private boolean abortExecution(boolean abortAll) {
        boolean aborted = false;
        for (Node node : nodeMapping) {
            Iterables.addAll(dependenciesWithChanges, node.getAllPredecessors());
            // Allow currently executing and enforced tasks to complete, but skip everything else.
            if (node.isRequired()) {
                node.skipExecution();
                aborted = true;
            }

            // If abortAll is set, also stop enforced tasks.
            if (abortAll && node.isReady()) {
                node.abortExecution();
                aborted = true;
            }
        }
        return aborted;
    }

    @Override
    public void collectFailures(Collection<? super Throwable> failures) {
        List<Throwable> collectedFailures = failureCollector.getFailures();
        failures.addAll(collectedFailures);
        if (buildCancelled && collectedFailures.isEmpty()) {
            failures.add(new BuildCancelledException());
        }
    }

    @Override
    public boolean allNodesComplete() {
        for (Node node : nodeMapping) {
            if (!node.isComplete()) {
                return false;
            }
        }
        // TODO:lptr why don't we check runningNodes here like we do in hasNodesRemaining()?
        return true;
    }

    @Override
    public boolean hasNodesRemaining() {
        for (Node node : executionQueue) {
            if (!node.isComplete()) {
                return true;
            }
        }
        return !runningNodes.isEmpty();
    }

    @Override
    public int size() {
        return nodeMapping.getNumberOfPublicNodes();
    }

    private static class GraphEdge {
        private final Node from;
        private final Node to;

        private GraphEdge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class NodeInVisitingSegment {
        private final Node node;
        private final int visitingSegment;

        private NodeInVisitingSegment(Node node, int visitingSegment) {
            this.node = node;
            this.visitingSegment = visitingSegment;
        }
    }

    private static class MutationInfo {
        final Node node;
        final Set<Node> consumingNodes = Sets.newHashSet();
        final Set<Node> producingNodes = Sets.newHashSet();
        final Set<String> outputPaths = Sets.newHashSet();
        final Set<String> destroyablePaths = Sets.newHashSet();
        boolean hasFileInputs;
        boolean hasOutputs;
        boolean hasLocalState;
        boolean resolved;

        MutationInfo(Node node) {
            this.node = node;
        }
    }

    private static class NodeMapping extends AbstractCollection<Node> {
        private final Map<Task, LocalTaskNode> taskMapping = Maps.newLinkedHashMap();
        private final Set<Node> nodes = Sets.newLinkedHashSet();

        @Override
        public boolean contains(Object o) {
            return nodes.contains(o);
        }

        @Override
        public boolean add(Node node) {
            if (!nodes.add(node)) {
                return false;
            }
            if (node instanceof LocalTaskNode) {
                LocalTaskNode taskNode = (LocalTaskNode) node;
                taskMapping.put(taskNode.getTask(), taskNode);
            }
            return true;
        }

        public TaskNode get(Task task) {
            TaskNode taskNode = taskMapping.get(task);
            if (taskNode == null) {
                throw new IllegalStateException("Task is not part of the execution plan, no dependency information is available.");
            }
            return taskNode;
        }

        public Set<Task> getTasks() {
            return taskMapping.keySet();
        }

        @Override
        public Iterator<Node> iterator() {
            return nodes.iterator();
        }

        @Override
        public void clear() {
            nodes.clear();
            taskMapping.clear();
        }

        @Override
        public int size() {
            return nodes.size();
        }

        public int getNumberOfPublicNodes() {
            int publicNodes = 0;
            for (Node node : this) {
                if (node.isPublicNode()) {
                    publicNodes++;
                }
            }
            return publicNodes;
        }

        public void retainFirst(int count) {
            Iterator<Node> executionPlanIterator = nodes.iterator();
            for (int i = 0; i < count; i++) {
                executionPlanIterator.next();
            }
            while (executionPlanIterator.hasNext()) {
                Node removedNode = executionPlanIterator.next();
                executionPlanIterator.remove();
                if (removedNode instanceof LocalTaskNode) {
                    taskMapping.remove(((LocalTaskNode) removedNode).getTask());
                }
            }
        }
    }
}
