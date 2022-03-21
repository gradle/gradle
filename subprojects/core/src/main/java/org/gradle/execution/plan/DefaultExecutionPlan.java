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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDestroyablesInternal;
import org.gradle.api.internal.tasks.TaskLocalStateInternal;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Pair;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.collect.Sets.newIdentityHashSet;

/**
 * The mutation methods on this implementation are NOT threadsafe, and callers must synchronize access to these methods.
 */
@NonNullApi
public class DefaultExecutionPlan implements ExecutionPlan {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutionPlan.class);

    private final Set<Node> entryNodes = new LinkedHashSet<>();
    private final NodeMapping nodeMapping = new NodeMapping();
    private final List<Node> executionQueue = new LinkedList<>();
    private final FailureCollector failureCollector = new FailureCollector();
    private final String displayName;
    private final TaskNodeFactory taskNodeFactory;
    private final TaskDependencyResolver dependencyResolver;
    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchy destroyableHierarchy;
    private final ResourceLockCoordinationService lockCoordinator;
    private final Action<ResourceLock> resourceUnlockListener = this::resourceUnlocked;
    private Spec<? super Task> filter = Specs.satisfyAll();
    private int order = 0;

    private boolean invalidNodeRunning;
    private boolean continueOnFailure;

    private final Set<Node> runningNodes = newIdentityHashSet();
    private final Set<Node> filteredNodes = newIdentityHashSet();
    private final Set<Node> producedButNotYetConsumed = newIdentityHashSet();
    private final Map<Pair<Node, Node>, Boolean> reachableCache = new HashMap<>();
    private final OrdinalNodeAccess ordinalNodeAccess = new OrdinalNodeAccess();
    private Consumer<LocalTaskNode> completionHandler = localTaskNode -> {
    };

    // When true, there may be nodes that are "ready", which means their dependencies have completed and the action is ready to execute
    // When false, there are definitely no nodes that are "ready"
    private boolean maybeNodesReady;

    // When true, there may be nodes that are both ready and "selectable", which means their project and resources are able to be locked
    // When false, there are definitely no nodes that are "selectable"
    private boolean maybeNodesSelectable;

    private boolean buildCancelled;

    public DefaultExecutionPlan(
        String displayName,
        TaskNodeFactory taskNodeFactory,
        TaskDependencyResolver dependencyResolver,
        ExecutionNodeAccessHierarchy outputHierarchy,
        ExecutionNodeAccessHierarchy destroyableHierarchy,
        ResourceLockCoordinationService lockCoordinator
    ) {
        this.displayName = displayName;
        this.taskNodeFactory = taskNodeFactory;
        this.dependencyResolver = dependencyResolver;
        this.outputHierarchy = outputHierarchy;
        this.destroyableHierarchy = destroyableHierarchy;
        this.lockCoordinator = lockCoordinator;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public TaskNode getNode(Task task) {
        return nodeMapping.get(task);
    }

    @Override
    public void addNodes(Collection<? extends Node> nodes) {
        Deque<Node> queue = new ArrayDeque<>(nodes);
        for (Node node : nodes) {
            assert node.getDependenciesProcessed() || node instanceof TaskInAnotherBuild;
            assert node.isInKnownState();
            if (node.isRequired()) {
                entryNodes.add(node);
            }
        }
        doAddNodes(queue);
    }

    @Override
    public void addEntryTasks(Collection<? extends Task> tasks) {
        addEntryTasks(tasks, order++);
    }

    @Override
    public void addEntryTasks(Collection<? extends Task> tasks, int ordinal) {
        final Deque<Node> queue = new ArrayDeque<>();

        for (Task task : sorted(tasks)) {
            TaskNode node = taskNodeFactory.getOrCreateNode(task);
            node.maybeSetOrdinal(ordinal);
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

    private List<Task> sorted(Collection<? extends Task> tasks) {
        List<Task> sortedTasks = new ArrayList<>(tasks);
        Collections.sort(sortedTasks);
        return sortedTasks;
    }

    private void doAddNodes(Deque<Node> queue) {
        Set<Node> nodesInUnknownState = Sets.newLinkedHashSet();
        final Set<Node> visiting = new HashSet<>();

        while (!queue.isEmpty()) {
            Node node = queue.getFirst();
            if (node.getDependenciesProcessed() || node.isAlreadyExecuted()) {
                // Have already visited this node or have already executed it - skip it
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
                node.prepareForExecution(this::monitoredNodeReady);
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
        Set<Node> visiting = new HashSet<>();

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

    @Override
    public void determineExecutionPlan() {
        LinkedList<NodeInVisitingSegment> nodeQueue = newLinkedList(
            Iterables.transform(entryNodes, new Function<Node, NodeInVisitingSegment>() {
                private int index;

                @Override
                @SuppressWarnings("NullableProblems")
                public NodeInVisitingSegment apply(Node node) {
                    return new NodeInVisitingSegment(node, index++);
                }
            })
        );
        int visitingSegmentCounter = nodeQueue.size();

        HashMultimap<Node, Integer> visitingNodes = HashMultimap.create();
        Deque<GraphEdge> walkedShouldRunAfterEdges = new ArrayDeque<>();
        Deque<Node> path = new ArrayDeque<>();
        Map<Node, Integer> planBeforeVisiting = new HashMap<>();

        while (!nodeQueue.isEmpty()) {
            NodeInVisitingSegment nodeInVisitingSegment = nodeQueue.peekFirst();
            int currentSegment = nodeInVisitingSegment.visitingSegment;
            Node node = nodeInVisitingSegment.node;

            if (!node.isIncludeInGraph() || nodeMapping.contains(node)) {
                nodeQueue.removeFirst();
                visitingNodes.remove(node, currentSegment);
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, node);
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
                    if (node instanceof TaskNode && successor instanceof TaskNode) {
                        ((TaskNode) successor).maybeInheritOrdinalAsDependency((TaskNode) node);
                    }
                }
                path.push(node);
            } else {
                // Have visited this node's dependencies - add it to the end of the plan
                nodeQueue.removeFirst();
                maybeRemoveProcessedShouldRunAfterEdge(walkedShouldRunAfterEdges, node);
                visitingNodes.remove(node, currentSegment);
                path.pop();
                nodeMapping.add(node);

                for (Node dependency : node.getDependencySuccessors()) {
                    dependency.getMutationInfo().consumingNodes.add(node);
                }

                // Add any finalizers to the queue
                for (Node finalizer : node.getFinalizers()) {
                    if (!visitingNodes.containsKey(finalizer)) {
                        int position = finalizerTaskPosition(finalizer, nodeQueue);
                        nodeQueue.add(position, new NodeInVisitingSegment(finalizer, visitingSegmentCounter++));
                        if (node instanceof TaskNode && finalizer instanceof TaskNode) {
                            ((TaskNode) finalizer).maybeInheritOrdinalAsFinalizer((TaskNode) node);
                        }
                    }
                }

                // Add any ordinal relationships for this node
                createOrdinalRelationships(node);
            }
        }
        ordinalNodeAccess.createInterNodeRelationships();
        nodeMapping.addAll(ordinalNodeAccess.getAllNodes());
        executionQueue.clear();
        dependencyResolver.clear();
        executionQueue.addAll(nodeMapping);

        for (Node node : executionQueue) {
            maybeNodesReady(node.updateAllDependenciesComplete() && node.isReady());
        }

        maybeNodesSelectable = true;
        lockCoordinator.addLockReleaseListener(resourceUnlockListener);
    }

    @Override
    public void close() {
        lockCoordinator.removeLockReleaseListener(resourceUnlockListener);
        completionHandler = localTaskNode -> {
        };
        entryNodes.clear();
        nodeMapping.clear();
        executionQueue.clear();
        runningNodes.clear();
        filteredNodes.clear();
        producedButNotYetConsumed.clear();
        reachableCache.clear();
    }

    private void resourceUnlocked(ResourceLock resourceLock) {
        if (!(resourceLock instanceof WorkerLeaseRegistry.WorkerLease) && maybeNodesReady) {
            maybeNodesSelectable = true;
        }
    }

    private void createOrdinalRelationships(Node node) {
        if (node instanceof TaskNode && ((TaskNode) node).getOrdinal() != TaskNode.UNKNOWN_ORDINAL) {
            TaskNode taskNode = (TaskNode) node;
            TaskClassifier taskClassifier = new TaskClassifier();
            ProjectInternal project = (ProjectInternal) taskNode.getTask().getProject();
            ServiceRegistry serviceRegistry = project.getServices();
            PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);

            // Walk the properties of the task to determine if it is a destroyer or a producer (or neither)
            propertyWalker.visitProperties(taskNode.getTask(), TypeValidationContext.NOOP, taskClassifier);
            taskNode.getTask().getOutputs().visitRegisteredProperties(taskClassifier);
            ((TaskDestroyablesInternal) taskNode.getTask().getDestroyables()).visitRegisteredProperties(taskClassifier);
            ((TaskLocalStateInternal) taskNode.getTask().getLocalState()).visitRegisteredProperties(taskClassifier);

            if (taskClassifier.isDestroyer()) {
                // Create (or get) a destroyer ordinal node that depends on the dependencies of this task node
                Node ordinalNode = ordinalNodeAccess.getOrCreateDestroyableLocationNode(taskNode.getOrdinal());
                taskNode.getHardSuccessors().forEach(ordinalNode::addDependencySuccessor);

                if (taskNode.getOrdinal() > 0) {
                    // Depend on any previous producer ordinal nodes (i.e. any producer ordinal nodes with a lower
                    // ordinal)
                    ordinalNodeAccess.getPrecedingProducerLocationNodes(taskNode.getOrdinal())
                        .forEach(taskNode::addDependencySuccessor);
                }
            } else if (taskClassifier.isProducer()) {
                // Create (or get) a producer ordinal node that depends on the dependencies of this task node
                Node ordinalNode = ordinalNodeAccess.getOrCreateOutputLocationNode(taskNode.getOrdinal());
                taskNode.getHardSuccessors().forEach(ordinalNode::addDependencySuccessor);

                if (taskNode.getOrdinal() > 0) {
                    // Depend on any previous destroyer ordinal nodes (i.e. any destroyer ordinal nodes with a lower
                    // ordinal)
                    ordinalNodeAccess.getPrecedingDestroyerLocationNodes(taskNode.getOrdinal())
                        .forEach(taskNode::addDependencySuccessor);
                }
            }
        }
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
        Set<Node> precedingNodes = new HashSet<>();
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
        graphWalker.add(successor);

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
        throw new CircularReferenceException(String.format("Circular dependency between the following tasks:%n%s", writer));
    }

    @Override
    public void onComplete(Consumer<LocalTaskNode> handler) {
        Consumer<LocalTaskNode> previous = this.completionHandler;
        this.completionHandler = node -> {
            previous.accept(node);
            handler.accept(node);
        };
    }

    @Override
    public Set<Task> getTasks() {
        return nodeMapping.getTasks();
    }

    @Override
    public Set<Task> getRequestedTasks() {
        Set<Task> requested = new LinkedHashSet<>();
        for (Node entryNode : entryNodes) {
            if (entryNode instanceof LocalTaskNode) {
                requested.add(((LocalTaskNode) entryNode).getTask());
            }
        }
        return requested;
    }

    @Override
    public List<Node> getScheduledNodes() {
        return ImmutableList.copyOf(nodeMapping.nodes);
    }

    @Override
    public List<Node> getScheduledNodesPlusDependencies() {
        Set<Node> nodes = nodeMapping.nodes;
        return ImmutableList.copyOf(nodes);
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

    @Override
    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    @Override
    public void setContinueOnFailure(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
    }

    @Override
    public State executionState() {
        lockCoordinator.assertHasStateLock();
        if (executionQueue.isEmpty()) {
            return State.NoMoreNodesToStart;
        } else if (maybeNodesSelectable) {
            return State.MaybeNodesReadyToStart;
        } else {
            return State.NoNodesReadyToStart;
        }
    }

    @Override
    public Diagnostics healthDiagnostics() {
        lockCoordinator.assertHasStateLock();
        State state = executionState();
        // If no nodes are ready and nothing is running, then cannot make progress
        boolean cannotMakeProgress = state == State.NoNodesReadyToStart && runningNodes.isEmpty();
        if (cannotMakeProgress) {
            List<String> nodes = new ArrayList<>(executionQueue.size());
            for (Node node : executionQueue) {
                if (!node.isReady()) {
                    nodes.add(node + " (not ready)");
                } else if (!node.allDependenciesComplete()) {
                    nodes.add(node + " (dependencies not complete)");
                } else {
                    nodes.add(node.toString());
                }
            }
            return new Diagnostics(false, nodes);
        } else {
            return new Diagnostics(true, Collections.emptyList());
        }
    }

    @Override
    public NodeSelection selectNext() {
        lockCoordinator.assertHasStateLock();
        if (executionQueue.isEmpty()) {
            return NO_MORE_NODES_TO_START;
        }
        if (!maybeNodesSelectable) {
            return NO_NODES_READY_TO_START;
        }

        List<ResourceLock> resources = new ArrayList<>();
        Iterator<Node> iterator = executionQueue.iterator();
        boolean foundReadyNode = false;
        boolean foundNotReadyNode = false;
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node.isReady() && node.allDependenciesComplete()) {
                if (!node.allDependenciesSuccessful()) {
                    // Cannot execute this node due to failed dependencies - skip it
                    node.skipExecution(this::recordNodeCompleted);
                    iterator.remove();
                    continue;
                }

                foundReadyNode = true;

                Node prepareNode = node.getPrepareNode();
                if (prepareNode != null) {
                    if (!prepareNode.isRequired()) {
                        prepareNode.require();
                    }
                    if (prepareNode.isReady()) {
                        if (attemptToStart(prepareNode, resources)) {
                            node.addDependencySuccessor(prepareNode);
                            node.forceAllDependenciesCompleteUpdate();
                            return NodeSelection.of(prepareNode);
                        } else {
                            // Cannot start prepare node, so skip to next node
                            continue;
                        }
                    }
                    // else prepare node has already completed
                }

                if (attemptToStart(node, resources)) {
                    iterator.remove();
                    return NodeSelection.of(node);
                }
            } else if (!node.isComplete()) {
                // Node is not yet complete
                // - its dependencies are not yet complete
                // - it is waiting for some external event such as completion of a task in another build
                foundNotReadyNode = true;
            }
        }

        LOGGER.debug("No node could be selected, nodes ready: {}", foundReadyNode);
        maybeNodesReady = foundReadyNode;
        maybeNodesSelectable = false;
        if (executionQueue.isEmpty()) {
            return NO_MORE_NODES_TO_START;
        } else if (!foundReadyNode && !foundNotReadyNode && runningNodes.isEmpty()) {
            // Nothing is currently running or will run -> all tasks that remain in the execution queue cannot run because
            // they are finalizers for tasks that could not be started
            executionQueue.clear();
            return NO_MORE_NODES_TO_START;
        } else {
            // Some tasks are yet to start
            // - they are ready to execute but cannot acquire the resources they need to start
            // - they are waiting for their dependencies to complete
            // - they are waiting for some external event
            return NO_NODES_READY_TO_START;
        }
    }

    private boolean attemptToStart(Node node, List<ResourceLock> resources) {
        resources.clear();
        if (!tryAcquireLocksForNode(node, resources)) {
            releaseLocks(resources);
            return false;
        }

        MutationInfo mutations = getResolvedMutationInfo(node);

        if (conflictsWithOtherNodes(node, mutations)) {
            releaseLocks(resources);
            return false;
        }

        node.startExecution(this::recordNodeExecutionStarted);
        if (mutations.hasValidationProblem) {
            invalidNodeRunning = true;
        }
        return true;
    }

    private void releaseLocks(List<ResourceLock> resources) {
        for (ResourceLock resource : resources) {
            resource.unlock();
        }
    }

    private boolean tryAcquireLocksForNode(Node node, List<ResourceLock> resources) {
        if (!tryLockProjectFor(node, resources)) {
            LOGGER.debug("Cannot acquire project lock for node {}", node);
            return false;
        } else if (!tryLockSharedResourceFor(node, resources)) {
            LOGGER.debug("Cannot acquire shared resource lock for node {}", node);
            return false;
        }
        return true;
    }

    private boolean conflictsWithOtherNodes(Node node, MutationInfo mutations) {
        if (!canRunWithCurrentlyExecutedNodes(mutations)) {
            LOGGER.debug("Node {} cannot run with currently running nodes {}", node, runningNodes);
            return true;
        } else if (destroysNotYetConsumedOutputOfAnotherNode(node, mutations.destroyablePaths)) {
            LOGGER.debug("Node {} destroys not yet consumed output of another node", node);
            return true;
        }
        return false;
    }

    private void updateAllDependenciesCompleteForPredecessors(Node node) {
        for (Node predecessor : node.getAllPredecessors()) {
            maybeNodesReady(predecessor.updateAllDependenciesComplete() && predecessor.isReady());
        }
    }

    private boolean tryLockProjectFor(Node node, List<ResourceLock> resources) {
        ResourceLock toLock = node.getProjectToLock();
        if (toLock == null) {
            return true;
        } else if (toLock.tryLock()) {
            resources.add(toLock);
            return true;
        } else {
            return false;
        }
    }

    private void unlockProjectFor(Node node) {
        ResourceLock toUnlock = node.getProjectToLock();
        if (toUnlock != null) {
            toUnlock.unlock();
        }
    }

    private boolean tryLockSharedResourceFor(Node node, List<ResourceLock> resources) {
        for (ResourceLock resource : node.getResourcesToLock()) {
            if (!resource.tryLock()) {
                return false;
            }
            resources.add(resource);
        }
        return true;
    }

    private void unlockSharedResourcesFor(Node node) {
        node.getResourcesToLock().forEach(ResourceLock::unlock);
    }

    private MutationInfo getResolvedMutationInfo(Node node) {
        MutationInfo mutations = node.getMutationInfo();
        if (!mutations.resolved) {
            outputHierarchy.recordNodeAccessingLocations(node, mutations.outputPaths);
            destroyableHierarchy.recordNodeAccessingLocations(node, mutations.destroyablePaths);
            mutations.resolved = true;
        }
        return mutations;
    }

    private boolean canRunWithCurrentlyExecutedNodes(MutationInfo mutations) {
        if (mutations.hasValidationProblem) {
            if (!runningNodes.isEmpty()) {
                // Invalid work is not allowed to run together with any other work
                return false;
            }
        } else if (invalidNodeRunning) {
            // No new work should be started when invalid work is running
            return false;
        }
        return !hasRunningNodeWithOverlappingMutations(mutations);
    }

    private boolean hasRunningNodeWithOverlappingMutations(MutationInfo mutations) {
        if (runningNodes.isEmpty()) {
            return false;
        }
        Set<String> candidateNodeOutputs = mutations.outputPaths;
        Set<String> candidateMutationPaths = !candidateNodeOutputs.isEmpty()
            ? candidateNodeOutputs
            : mutations.destroyablePaths;
        if (!candidateMutationPaths.isEmpty()) {
            for (String candidateMutationPath : candidateMutationPaths) {
                Stream<Node> nodesMutatingCandidatePath = Stream.concat(
                    outputHierarchy.getNodesAccessing(candidateMutationPath).stream(),
                    destroyableHierarchy.getNodesAccessing(candidateMutationPath).stream()
                );
                if (nodesMutatingCandidatePath.anyMatch(runningNodes::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean destroysNotYetConsumedOutputOfAnotherNode(Node destroyer, Set<String> destroyablePaths) {
        if (destroyablePaths.isEmpty()) {
            return false;
        }
        for (String destroyablePath : destroyablePaths) {
            ImmutableSet<Node> producersDestroyedByDestroyer = outputHierarchy.getNodesAccessing(destroyablePath);
            for (Node producingNode : producedButNotYetConsumed) {
                if (!producersDestroyedByDestroyer.contains(producingNode)) {
                    continue;
                }
                MutationInfo producingNodeMutations = producingNode.getMutationInfo();
                assert !producingNodeMutations.consumingNodes.isEmpty();
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

        if (node instanceof LocalTaskNode) {
            completionHandler.accept((LocalTaskNode) node);
        }
    }

    private void monitoredNodeReady(Node node) {
        lockCoordinator.assertHasStateLock();
        maybeNodesReady(true);
    }

    @Override
    public void finishedExecuting(Node node) {
        lockCoordinator.assertHasStateLock();
        if (!node.isExecuting()) {
            throw new IllegalStateException(String.format("Cannot finish executing %s as it is in an unexpected state.", node));
        }
        try {
            if (maybeNodesReady) {
                maybeNodesSelectable = true;
            }
            enforceFinalizers(node);
            runningNodes.remove(node);
            node.finishExecution(this::recordNodeCompleted);
            if (node.isFailed()) {
                LOGGER.debug("Node {} failed", node);
                handleFailure(node);
            } else {
                LOGGER.debug("Node {} finished executing", node);
            }
        } finally {
            unlockProjectFor(node);
            unlockSharedResourcesFor(node);
            invalidNodeRunning = false;
        }
    }

    private void maybeNodesReady(boolean ready) {
        if (ready) {
            maybeNodesReady = true;
            maybeNodesSelectable = true;
        }
    }

    private void enforceFinalizers(Node node) {
        for (Node finalizerNode : node.getFinalizers()) {
            if (finalizerNode.isRequired() || finalizerNode.isMustNotRun()) {
                enforceWithDependencies(finalizerNode);
            }
        }
    }

    private void enforceWithDependencies(Node node) {
        Set<Node> enforcedNodes = new HashSet<>();

        Deque<Node> candidates = new ArrayDeque<>();
        candidates.add(node);

        while (!candidates.isEmpty()) {
            Node candidate = candidates.pop();
            if (!enforcedNodes.contains(candidate)) {
                enforcedNodes.add(candidate);

                candidates.addAll(candidate.getDependencySuccessors());

                if (candidate.isMustNotRun() || candidate.isRequired()) {
                    maybeNodesReady(true);
                    candidate.enforceRun();
                    // Completed changed from true to false - inform all nodes depending on this one.
                    for (Node predecessor : candidate.getAllPredecessors()) {
                        predecessor.forceAllDependenciesCompleteUpdate();
                    }
                }
            }
        }
    }

    @Override
    public void abortAllAndFail(Throwable t) {
        lockCoordinator.assertHasStateLock();
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
        lockCoordinator.assertHasStateLock();
        buildCancelled = abortExecution() || buildCancelled;
    }

    private boolean abortExecution(boolean abortAll) {
        boolean aborted = false;
        Iterator<Node> iterator = executionQueue.iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();

            // Allow currently executing and enforced tasks to complete, but skip everything else.
            if (node.isRequired()) {
                node.skipExecution(this::recordNodeCompleted);
                iterator.remove();
                aborted = true;
            }

            // If abortAll is set, also stop enforced tasks.
            if (abortAll && node.isReady()) {
                node.abortExecution(this::recordNodeCompleted);
                iterator.remove();
                aborted = true;
            }
        }
        if (aborted) {
            maybeNodesSelectable = true;
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
    public boolean allExecutionComplete() {
        return executionQueue.isEmpty() && runningNodes.isEmpty();
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

    private static class TaskClassifier extends PropertyVisitor.Adapter {
        private boolean isProducer;
        private boolean isDestroyer;

        @Override
        public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
            isProducer = true;
        }

        @Override
        public void visitDestroyableProperty(Object value) {
            isDestroyer = true;
        }

        @Override
        public void visitLocalStateProperty(Object value) {
            isProducer = true;
        }

        public boolean isProducer() {
            return isProducer;
        }

        public boolean isDestroyer() {
            return isDestroyer;
        }
    }
}
