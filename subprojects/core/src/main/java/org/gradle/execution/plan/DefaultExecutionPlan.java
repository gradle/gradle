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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Pair;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.StringWriter;
import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reusable implementation of ExecutionPlan. The {@link #addEntryTasks(java.util.Collection)} and {@link #clear()} methods are NOT threadsafe, and callers must synchronize access to these methods.
 */
@NonNullApi
public class DefaultExecutionPlan implements ExecutionPlan {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutionPlan.class);

    private final Set<Node> entryNodes = new LinkedHashSet<>();
    private final NodeMapping nodeMapping = new NodeMapping();
    private final List<Node> executionQueue = Lists.newLinkedList();
    private final Set<ResourceLock> projectLocks = Sets.newHashSet();
    private final FailureCollector failureCollector = new FailureCollector();
    private final TaskNodeFactory taskNodeFactory;
    private final TaskDependencyResolver dependencyResolver;
    private Spec<? super Task> filter = Specs.satisfyAll();

    private boolean continueOnFailure;

    private final Set<Node> runningNodes = Sets.newIdentityHashSet();
    private final Set<Node> filteredNodes = Sets.newIdentityHashSet();
    private final Set<Node> producedButNotYetConsumed = Sets.newIdentityHashSet();
    private final Map<Pair<Node, Node>, Boolean> reachableCache = Maps.newHashMap();
    private final List<Node> dependenciesWhichRequireMonitoring = Lists.newArrayList();
    private boolean maybeNodesReady;
    private final GradleInternal gradle;

    private boolean buildCancelled;

    public DefaultExecutionPlan(GradleInternal gradle, TaskNodeFactory taskNodeFactory, TaskDependencyResolver dependencyResolver) {
        this.gradle = gradle;
        this.taskNodeFactory = taskNodeFactory;
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public String getDisplayName() {
        return gradle.getIdentityPath().toString();
    }

    @Override
    public TaskNode getNode(Task task) {
        return nodeMapping.get(task);
    }

    public void addNodes(Collection<? extends Node> nodes) {
        Deque<Node> queue = new ArrayDeque<>(nodes);
        for (Node node : nodes) {
            assert node.getDependenciesProcessed();
            assert node.isInKnownState();
            if (node.isRequired()) {
                entryNodes.add(node);
            }
        }
        doAddNodes(queue);
    }

    public void addEntryTasks(Collection<? extends Task> tasks) {
        final Deque<Node> queue = new ArrayDeque<>();

        List<Task> sortedTasks = new ArrayList<>(tasks);
        Collections.sort(sortedTasks);
        for (Task task : sortedTasks) {
            TaskNode node = taskNodeFactory.getOrCreateNode(task);
            if (node.isMustNotRun()) {
                requireWithDependencies(node);
            } else if (filter.isSatisfiedBy(task)) {
                node.require();
            }
            entryNodes.add(node);
            queue.add(node);
        }

        doAddNodes(queue);
    }

    private void doAddNodes(Deque<Node> queue) {
        Set<Node> nodesInUnknownState = Sets.newLinkedHashSet();
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
                node.resolveDependencies(dependencyResolver, targetNode -> {
                    if (!visiting.contains(targetNode)) {
                        queue.addFirst(targetNode);
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
        Deque<Node> queue = new ArrayDeque<>(nodesInUnknownState);
        Set<Node> visiting = Sets.newHashSet();

        while (!queue.isEmpty()) {
            Node node = queue.peekFirst();
            if (node.isInKnownState()) {
                queue.removeFirst();
                continue;
            }

            if (visiting.add(node)) {
                for (Node hardPredecessor : node.getDependencyPredecessors()) {
                    if (!visiting.contains(hardPredecessor)) {
                        queue.addFirst(hardPredecessor);
                    }
                }
            } else {
                queue.removeFirst();
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
        LinkedList<NodeInVisitingSegment> nodeQueue = Lists.newLinkedList(Iterables.transform(entryNodes, new Function<Node, NodeInVisitingSegment>() {
            private int index;

            @Override
            @SuppressWarnings("NullableProblems")
            public NodeInVisitingSegment apply(Node node) {
                return new NodeInVisitingSegment(node, index++);
            }
        }));
        int visitingSegmentCounter = nodeQueue.size();
        Set<Node> dependenciesWhichRequireMonitoring = Sets.newHashSet();

        HashMultimap<Node, Integer> visitingNodes = HashMultimap.create();
        Deque<GraphEdge> walkedShouldRunAfterEdges = new ArrayDeque<>();
        Deque<Node> path = new ArrayDeque<>();
        Map<Node, Integer> planBeforeVisiting = Maps.newHashMap();

        while (!nodeQueue.isEmpty()) {
            NodeInVisitingSegment nodeInVisitingSegment = nodeQueue.peekFirst();
            int currentSegment = nodeInVisitingSegment.visitingSegment;
            Node node = nodeInVisitingSegment.node;

            if (!node.isIncludeInGraph() || nodeMapping.contains(node)) {
                nodeQueue.removeFirst();
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
                    nodeQueue.addFirst(new NodeInVisitingSegment(successor, currentSegment));
                }
                path.push(node);
            } else {
                // Have visited this node's dependencies - add it to the end of the plan
                nodeQueue.removeFirst();
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, node);
                visitingNodes.remove(node, currentSegment);
                path.pop();
                nodeMapping.add(node);
                if (node.requiresMonitoring()) {
                    dependenciesWhichRequireMonitoring.add(node);
                }

                for (Node dependency : node.getDependencySuccessors()) {
                    dependency.getMutationInfo().consumingNodes.add(node);
                }

                ResourceLock projectLock = node.getProjectToLock();
                if (projectLock != null) {
                    projectLocks.add(projectLock);
                }

                // Add any finalizers to the queue
                for (Node finalizer : node.getFinalizers()) {
                    if (!visitingNodes.containsKey(finalizer)) {
                        int position = finalizerTaskPosition(finalizer, nodeQueue);
                        nodeQueue.add(position, new NodeInVisitingSegment(finalizer, visitingSegmentCounter++));
                    }
                }
            }
        }
        executionQueue.clear();
        dependencyResolver.clear();
        Iterables.addAll(executionQueue, nodeMapping);
        for (Node node : executionQueue) {
            maybeNodesReady |= node.updateAllDependenciesComplete() && node.isReady();
        }
        this.dependenciesWhichRequireMonitoring.addAll(dependenciesWhichRequireMonitoring);
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

    private void restoreQueue(Deque<NodeInVisitingSegment> nodeQueue, HashMultimap<Node, Integer> visitingNodes, GraphEdge toBeRemoved) {
        NodeInVisitingSegment nextInQueue = null;
        while (nextInQueue == null || !toBeRemoved.from.equals(nextInQueue.node)) {
            nextInQueue = nodeQueue.peekFirst();
            visitingNodes.remove(nextInQueue.node, nextInQueue.visitingSegment);
            if (!toBeRemoved.from.equals(nextInQueue.node)) {
                nodeQueue.removeFirst();
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
        Iterables.removeIf(
            ((TaskNode) node).getShouldSuccessors(),
            input -> visitingNodes.containsEntry(input, nodeWithVisitingSegment.visitingSegment)
        );
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
    private int finalizerTaskPosition(Node finalizer, final Deque<NodeInVisitingSegment> nodeQueue) {
        if (nodeQueue.size() == 0) {
            return 0;
        }

        Set<Node> precedingTasks = getAllPrecedingNodes(finalizer);
        int maxPrecedingTaskIndex = precedingTasks.stream()
            .mapToInt(dependsOnTask -> Iterables.indexOf(nodeQueue, nodeInVisitingSegment -> nodeInVisitingSegment.node.equals(dependsOnTask)))
            .max()
            .orElseThrow(IllegalStateException::new);

        return maxPrecedingTaskIndex + 1;
    }

    private Set<Node> getAllPrecedingNodes(Node finalizer) {
        Set<Node> precedingNodes = Sets.newHashSet();
        Deque<Node> candidateNodes = new ArrayDeque<>();

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

    private void onOrderingCycle(Node successor, Node currentNode) {
        CachingDirectedGraphWalker<Node, Void> graphWalker = new CachingDirectedGraphWalker<>((node, values, connectedNodes) -> {
            connectedNodes.addAll(node.getDependencySuccessors());
            if (node instanceof TaskNode) {
                TaskNode taskNode = (TaskNode) node;
                connectedNodes.addAll(taskNode.getMustSuccessors());
                connectedNodes.addAll(taskNode.getFinalizingSuccessors());
            }
        });
        graphWalker.add(entryNodes);

        List<Set<Node>> cycles = graphWalker.findCycles();
        if (cycles.isEmpty()) {
            // TODO: This isn't correct. This means that we've detected a cycle while determining the execution plan, but the graph walker did not find one.
            // https://github.com/gradle/gradle/issues/2293
            throw new GradleException("Misdetected cycle between " + currentNode + " and " + successor + ". Help us by reporting this to https://github.com/gradle/gradle/issues/2293");
        }
        List<Node> firstCycle = new ArrayList<>(cycles.get(0));
        Collections.sort(firstCycle);

        DirectedGraphRenderer<Node> graphRenderer = new DirectedGraphRenderer<>(
            (it, output) -> output.withStyle(StyledTextOutput.Style.Identifier).text(it),
            (it, values, connectedNodes) -> {
                for (Node dependency : firstCycle) {
                    if (it.hasHardSuccessor(dependency)) {
                        connectedNodes.add(dependency);
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
        entryNodes.clear();
        nodeMapping.clear();
        executionQueue.clear();
        projectLocks.clear();
        failureCollector.clearFailures();
        producedButNotYetConsumed.clear();
        reachableCache.clear();
        dependenciesWhichRequireMonitoring.clear();
        runningNodes.clear();
    }

    @Override
    public Set<Task> getTasks() {
        return nodeMapping.getTasks();
    }

    public List<Node> getScheduledNodes() {
        return ImmutableList.copyOf(nodeMapping.nodes);
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
            // TODO - this is incorrect. We can still run nodes that don't need a project lock
            return null;
        }

        for (Iterator<Node> iterator = dependenciesWhichRequireMonitoring.iterator(); iterator.hasNext();) {
            Node node = iterator.next();
            if (node.isComplete()) {
                LOGGER.debug("Monitored node {} completed", node);
                updateAllDependenciesCompleteForPredecessors(node);
                iterator.remove();
            }
        }
        if (!maybeNodesReady) {
            return null;
        }
        Iterator<Node> iterator = executionQueue.iterator();
        boolean foundReadyNode = false;
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node.isReady() && node.allDependenciesComplete()) {
                foundReadyNode = true;
                MutationInfo mutations = getResolvedMutationInfo(node);

                if (!tryAcquireLocksForNode(node, workerLease, mutations)) {
                    resourceLockState.releaseLocks();
                    continue;
                }

                if (node.allDependenciesSuccessful()) {
                    node.startExecution(this::recordNodeExecutionStarted);
                } else {
                    node.skipExecution(this::recordNodeCompleted);
                }
                iterator.remove();
                return node;
            }
        }
        LOGGER.debug("No node could be selected, nodes ready: {}", foundReadyNode);
        maybeNodesReady = foundReadyNode;
        return null;
    }

    private boolean tryAcquireLocksForNode(Node node, WorkerLeaseRegistry.WorkerLease workerLease, MutationInfo mutations) {
        if (!tryLockProjectFor(node)) {
            LOGGER.debug("Cannot acquire project lock for node {}", node);
            return false;
        } else if (!tryLockSharedResourceFor(node)) {
            LOGGER.debug("Cannot acquire shared resource lock for node {}", node);
            return false;
        } else if (!workerLease.tryLock()) {
            LOGGER.debug("Cannot acquire worker lease lock for node {}", node);
            return false;
            // TODO: convert output file checks to a resource lock
        } else if (!canRunWithCurrentlyExecutedNodes(node, mutations)) {
            LOGGER.debug("Node {} cannot run with currently running nodes {}", node, runningNodes);
            return false;
        }
        return true;
    }

    private void updateAllDependenciesCompleteForPredecessors(Node node) {
        for (Node predecessor : node.getAllPredecessors()) {
            maybeNodesReady |= predecessor.updateAllDependenciesComplete() && predecessor.isReady();
        }
    }

    private boolean tryLockProjectFor(Node node) {
        ResourceLock toLock = node.getProjectToLock();
        if (toLock != null) {
            return toLock.tryLock();
        } else {
            return true;
        }
    }

    private void unlockProjectFor(Node node) {
        ResourceLock toUnlock = node.getProjectToLock();
        if (toUnlock != null) {
            toUnlock.unlock();
        }
    }

    private boolean tryLockSharedResourceFor(Node node) {
        return node.getResourcesToLock().stream().allMatch(ResourceLock::tryLock);
    }

    private void unlockSharedResourcesFor(Node node) {
        node.getResourcesToLock().forEach(ResourceLock::unlock);
    }

    private MutationInfo getResolvedMutationInfo(Node node) {
        MutationInfo mutations = node.getMutationInfo();
        if (!mutations.resolved) {
            node.resolveMutations();
        }
        return mutations;
    }

    private boolean allProjectsLocked() {
        for (ResourceLock lock : projectLocks) {
            if (!lock.isLocked()) {
                return false;
            }
        }
        return !projectLocks.isEmpty();
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

    private boolean hasNodeWithOverlappingMutations(Set<String> candidateMutationPaths) {
        if (!candidateMutationPaths.isEmpty()) {
            for (Node runningNode : runningNodes) {
                MutationInfo runningMutations = runningNode.getMutationInfo();
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
            for (Node producingNode : producedButNotYetConsumed) {
                MutationInfo producingNodeMutations = producingNode.getMutationInfo();
                assert !producingNodeMutations.consumingNodes.isEmpty();
                if (!hasOverlap(destroyablePaths, producingNodeMutations.outputPaths)) {
                    // No overlap no cry
                    continue;
                }
                for (Node consumer : producingNodeMutations.consumingNodes) {
                    if (doesConsumerDependOnDestroyer(consumer, destroyer)) {
                        // If there's an explicit dependency from consuming node to destroyer,
                        // then we accept that as the will of the user
                        continue;
                    }
                    LOGGER.debug("Node {} destroys output of consumer {}", destroyer, consumer);
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

    private void recordNodeExecutionStarted(Node node) {
        runningNodes.add(node);
    }

    private void recordNodeCompleted(Node node) {
        LOGGER.debug("Node {} completed, executed: {}", node, node.isExecuted());
        MutationInfo mutations = node.getMutationInfo();
        for (Node producer : node.getDependencySuccessors()) {
            MutationInfo producerMutations = producer.getMutationInfo();
            if (producerMutations.consumingNodes.remove(node) && producerMutations.consumingNodes.isEmpty()) {
                producedButNotYetConsumed.remove(producer);
            }
        }

        if (!mutations.consumingNodes.isEmpty() && !mutations.outputPaths.isEmpty()) {
            producedButNotYetConsumed.add(node);
        }

        updateAllDependenciesCompleteForPredecessors(node);
    }

    @Override
    public void finishedExecuting(Node node) {
        try {
            if (!node.isComplete()) {
                enforceFinalizers(node);
                maybeNodesReady = true;
                if (node.isFailed()) {
                    LOGGER.debug("Node {} failed", node);
                    handleFailure(node);
                } else {
                    LOGGER.debug("Node {} finished executing", node);
                }

                runningNodes.remove(node);
                node.finishExecution(this::recordNodeCompleted);
            } else {
                LOGGER.debug("Already completed node {} reported as finished executing", node);
            }
        } finally {
            unlockProjectFor(node);
            unlockSharedResourcesFor(node);
        }
    }

    private static void enforceFinalizers(Node node) {
        for (Node finalizerNode : node.getFinalizers()) {
            if (finalizerNode.isRequired() || finalizerNode.isMustNotRun()) {
                Set<Node> enforcedNodes = Sets.newHashSet();
                enforceWithDependencies(finalizerNode, enforcedNodes);
            }
        }
    }

    private static void enforceWithDependencies(Node nodeInfo, Set<Node> enforcedNodes) {
        Deque<Node> candidateNodes = new ArrayDeque<>();
        candidateNodes.add(nodeInfo);

        while (!candidateNodes.isEmpty()) {
            Node node = candidateNodes.pop();
            if (!enforcedNodes.contains(node)) {
                enforcedNodes.add(node);

                candidateNodes.addAll(node.getDependencySuccessors());

                if (node.isMustNotRun() || node.isRequired()) {
                    node.enforceRun();
                    // Completed changed from true to false - inform all nodes depending on this one.
                    for (Node predecessor : node.getAllPredecessors()) {
                        predecessor.forceAllDependenciesCompleteUpdate();
                    }
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
            // Allow currently executing and enforced tasks to complete, but skip everything else.
            if (node.isRequired()) {
                node.skipExecution(this::recordNodeCompleted);
                aborted = true;
            }

            // If abortAll is set, also stop enforced tasks.
            if (abortAll && node.isReady()) {
                node.abortExecution(this::recordNodeCompleted);
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
