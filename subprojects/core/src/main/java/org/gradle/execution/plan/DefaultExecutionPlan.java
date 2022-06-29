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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Pair;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.google.common.collect.Sets.newIdentityHashSet;
import static java.lang.String.format;

/**
 * The mutation methods on this implementation are NOT threadsafe, and callers must synchronize access to these methods.
 */
@NonNullApi
public class DefaultExecutionPlan implements ExecutionPlan, WorkSource<Node> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutionPlan.class);

    private final Set<Node> entryNodes = new LinkedHashSet<>();
    private final NodeMapping nodeMapping = new NodeMapping();
    private final ExecutionQueue executionQueue = new ExecutionQueue();
    private final List<Throwable> failures = new ArrayList<>();
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
    private final Set<Node> finalizers = new LinkedHashSet<>();
    private final Set<Node> preExecutionNodesVisited = new HashSet<>();
    private final OrdinalNodeAccess ordinalNodeAccess;
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
        OrdinalGroupFactory ordinalGroupFactory,
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
        this.ordinalNodeAccess = new OrdinalNodeAccess(ordinalGroupFactory);
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
        Deque<Node> queue = new ArrayDeque<>(nodes.size());
        for (Node node : nodes) {
            assert node.isInKnownState();
            if (node.isRequired()) {
                entryNodes.add(node);
                queue.add(node);
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
        final OrdinalGroup group = ordinalNodeAccess.group(ordinal);

        for (Task task : sorted(tasks)) {
            TaskNode node = taskNodeFactory.getOrCreateNode(task);
            node.setGroup(group);
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
        Set<Node> visiting = new HashSet<>();
        while (!queue.isEmpty()) {
            Node node = queue.getFirst();
            if (node.getDependenciesProcessed() || node.isCannotRunInAnyPlan()) {
                // Have already visited this node or have already executed it - skip it
                queue.removeFirst();
                continue;
            }

            boolean filtered = !nodeSatisfiesTaskFilter(node);
            if (filtered) {
                // Task is not required - skip it
                queue.removeFirst();
                node.dependenciesProcessed();
                node.filtered();
                filteredNodes.add(node);
                continue;
            }
            node.require();

            if (visiting.add(node)) {
                // Have not seen this node before - add its dependencies to the head of the queue and leave this
                // node in the queue
                // Make sure it has been configured
                node.prepareForExecution(this::monitoredNodeReady);
                node.resolveDependencies(dependencyResolver);
                for (Node successor : node.getHardSuccessors()) {
                    successor.maybeInheritOrdinalAsDependency(node.getGroup());
                }
                for (Node successor : node.getDependencySuccessorsInReverseOrder()) {
                    if (!visiting.contains(successor)) {
                        queue.addFirst(successor);
                    }
                }
            } else {
                // Have visited this node's dependencies - add it to the graph
                queue.removeFirst();
                visiting.remove(node);
                node.dependenciesProcessed();
                // Finalizers run immediately after the node
                for (Node finalizer : node.getFinalizers()) {
                    finalizers.add(finalizer);
                    if (!visiting.contains(finalizer)) {
                        queue.addFirst(finalizer);
                    }
                }
            }
        }
    }

    private boolean nodeSatisfiesTaskFilter(Node successor) {
        if (successor instanceof LocalTaskNode) {
            return filter.isSatisfiedBy(((LocalTaskNode) successor).getTask());
        }
        return true;
    }

    @Override
    public void determineExecutionPlan() {
        new DetermineExecutionPlanAction(
            nodeMapping,
            ordinalNodeAccess,
            entryNodes,
            finalizers
        ).run();
        dependencyResolver.clear();
        executionQueue.setNodes(nodeMapping);
    }

    @Override
    public void finalizePlan() {
        executionQueue.restart();
        while (executionQueue.hasNext()) {
            Node node = executionQueue.next();
            node.updateAllDependenciesComplete();
            maybeNodeReady(node);
        }
        lockCoordinator.addLockReleaseListener(resourceUnlockListener);
    }

    @Override
    public WorkSource<Node> asWorkSource() {
        // For now
        return this;
    }

    @Override
    public void close() {
        lockCoordinator.removeLockReleaseListener(resourceUnlockListener);
        completionHandler = localTaskNode -> {
        };
        for (Node node : nodeMapping) {
            node.reset();
        }
        entryNodes.clear();
        nodeMapping.clear();
        executionQueue.clear();
        runningNodes.clear();
        for (Node node : filteredNodes) {
            node.reset();
        }
        filteredNodes.clear();
        producedButNotYetConsumed.clear();
        reachableCache.clear();
        finalizers.clear();
        preExecutionNodesVisited.clear();
    }

    private void resourceUnlocked(ResourceLock resourceLock) {
        if (!(resourceLock instanceof WorkerLeaseRegistry.WorkerLease) && maybeNodesReady) {
            maybeNodesSelectable = true;
        }
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
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (Node entryNode : entryNodes) {
            if (entryNode instanceof LocalTaskNode) {
                builder.add(((LocalTaskNode) entryNode).getTask());
            }
        }
        return builder.build();
    }

    @Override
    public ScheduledNodes getScheduledNodes() {
        // Take an immutable copy of the nodes, as the set of nodes for this plan can be mutated (e.g. if the result is used after execution has completed and clear() has been called).
        ImmutableList.Builder<Node> builder = ImmutableList.builderWithExpectedSize(nodeMapping.nodes.size());
        for (Node node : nodeMapping.nodes) {
            // Do not include a task from another build when that task has already executed
            // Most nodes that have already executed are filtered in `doAddNodes()` but these particular nodes are node
            // It would be better to also remove these nodes in `doAddNodes()`
            if (node instanceof TaskInAnotherBuild && ((TaskInAnotherBuild) node).getTask().getState().getExecuted()) {
                continue;
            }
            builder.add(node);
        }
        return visitor -> lockCoordinator.withStateLock(() -> visitor.accept(builder.build()));
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
            return State.NoMoreWorkToStart;
        } else if (maybeNodesSelectable) {
            return State.MaybeWorkReadyToStart;
        } else {
            return State.NoWorkReadyToStart;
        }
    }

    @Override
    public Diagnostics healthDiagnostics() {
        lockCoordinator.assertHasStateLock();
        State state = executionState();
        // If no nodes are ready and nothing is running, then cannot make progress
        boolean cannotMakeProgress = state == State.NoWorkReadyToStart && runningNodes.isEmpty();
        if (cannotMakeProgress) {
            List<String> queuedNodes = new ArrayList<>(executionQueue.size());
            List<String> otherNodes = new ArrayList<>();
            List<Node> queue = new ArrayList<>();
            Set<Node> reported = new HashSet<>();
            executionQueue.restart();
            while (executionQueue.hasNext()) {
                Node node = executionQueue.next();
                queuedNodes.add(node.healthDiagnostics());
                reported.add(node);
                for (Node successor : node.getHardSuccessors()) {
                    queue.add(successor);
                }
            }
            while (!queue.isEmpty()) {
                Node node = queue.remove(0);
                if (reported.add(node)) {
                    otherNodes.add(node.healthDiagnostics());
                    for (Node successor : node.getHardSuccessors()) {
                        queue.add(successor);
                    }
                }
            }
            return new Diagnostics(displayName, false, queuedNodes, otherNodes);
        } else {
            return new Diagnostics(displayName);
        }
    }

    @Override
    public Selection<Node> selectNext() {
        lockCoordinator.assertHasStateLock();
        if (executionQueue.isEmpty()) {
            return Selection.noMoreWorkToStart();
        }
        if (!maybeNodesSelectable) {
            return Selection.noWorkReadyToStart();
        }

        List<ResourceLock> resources = new ArrayList<>();
        boolean foundReadyNode = false;
        executionQueue.restart();
        while (executionQueue.hasNext()) {
            Node node = executionQueue.next();
            if (node.allDependenciesComplete()) {
                if (!node.allDependenciesSuccessful()) {
                    // Cannot execute this node due to failed dependencies - skip it
                    if (node.shouldCancelExecutionDueToDependencies()) {
                        node.cancelExecution(this::recordNodeCompleted);
                    } else {
                        node.markFailedDueToDependencies(this::recordNodeCompleted);
                    }
                    executionQueue.remove();
                    // Skipped some nodes, which may invalidate some earlier nodes (for example a shared dependency of multiple finalizers when all finalizers are skipped), so start again
                    executionQueue.restart();
                    continue;
                }

                if (preExecutionNodesVisited.add(node)) {
                    // The node is ready to execute and its pre-execution nodes have not been scheduled, so do this now
                    executionQueue.startInsert();
                    node.visitPreExecutionNodes(prepareNode -> {
                        prepareNode.require();
                        prepareNode.updateAllDependenciesComplete();
                        node.addDependencySuccessor(prepareNode);
                        executionQueue.insert(prepareNode);
                    });
                    if (executionQueue.restartFromInsertPoint()) {
                        // Some pre-execution nodes were scheduled, so try to execute them now
                        node.forceAllDependenciesCompleteUpdate();
                        continue;
                    }
                }

                // Node is read to execute and all dependencies and pre-execution nodes have completed
                foundReadyNode = true;
                if (attemptToStart(node, resources)) {
                    executionQueue.remove();
                    return Selection.of(node);
                }
            }
            if (node.isComplete()) {
                // Is already complete
                // - is a pre-execution node that is also scheduled but not yet executed
                executionQueue.remove();
            }
            // Else, node is not yet complete
            // - its dependencies are not yet complete
            // - it is waiting for some external event such as completion of a task in another build
            // - it is a finalizer for nodes that are not yet complete
        }

        LOGGER.debug("No node could be selected, nodes ready: {}", foundReadyNode);
        maybeNodesReady = foundReadyNode;
        maybeNodesSelectable = false;
        if (executionQueue.isEmpty()) {
            return Selection.noMoreWorkToStart();
        } else {
            // No nodes are able to start
            // - they are ready to execute but cannot acquire the resources they need to start
            // - they are waiting for their dependencies to complete
            // - they are waiting for some external event
            // - they are a finalizer for nodes that are not yet complete
            return Selection.noWorkReadyToStart();
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
        node.visitAllNodesWaitingForThisNode(dependent -> {
            if (dependent.updateAllDependenciesComplete()) {
                maybeNodeReady(dependent);
            }
        });
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
            try {
                completionHandler.accept((LocalTaskNode) node);
            } catch (Throwable t) {
                failures.add(t);
            }
        }
    }

    private void monitoredNodeReady(Node node) {
        lockCoordinator.assertHasStateLock();
        if (node.updateAllDependenciesComplete()) {
            maybeNodeReady(node);
        }
    }

    @Override
    public void finishedExecuting(Node node, @Nullable Throwable failure) {
        lockCoordinator.assertHasStateLock();
        if (failure != null) {
            node.setExecutionFailure(failure);
        }
        if (!node.isExecuting()) {
            throw new IllegalStateException(format("Cannot finish executing %s as it is in an unexpected state.", node));
        }
        try {
            if (maybeNodesReady) {
                maybeNodesSelectable = true;
            }
            runningNodes.remove(node);
            node.finishExecution(this::recordNodeCompleted);
            if (node.isFailed()) {
                LOGGER.debug("Node {} failed", node);
                handleFailure(node);
            } else {
                LOGGER.debug("Node {} finished executing", node);
                executionQueue.restart();
                executionQueue.startInsert();
                node.visitPostExecutionNodes(postNode -> {
                    postNode.require();
                    postNode.updateAllDependenciesComplete();
                    for (Node predecessor : node.getDependencyPredecessors()) {
                        predecessor.addDependencySuccessor(postNode);
                        predecessor.forceAllDependenciesCompleteUpdate();
                    }
                    executionQueue.insert(postNode);
                });
            }
        } finally {
            unlockProjectFor(node);
            unlockSharedResourcesFor(node);
            invalidNodeRunning = false;
        }
    }

    private void maybeNodeReady(Node node) {
        if (node.allDependenciesComplete()) {
            maybeNodesReady = true;
            maybeNodesSelectable = true;
            if (node.isPriority()) {
                executionQueue.priorityNode(node);
            }
        }
    }

    private void handleFailure(Node node) {
        Throwable executionFailure = node.getExecutionFailure();
        if (executionFailure != null) {
            // Always abort execution for an execution failure (as opposed to a node failure)
            failures.add(executionFailure);
            abortExecution();
            return;
        }

        // Failure
        Throwable nodeFailure = node.getNodeFailure();
        if (nodeFailure != null) {
            failures.add(node.getNodeFailure());
            if (!continueOnFailure) {
                abortExecution();
            }
        }
    }

    private boolean abortExecution() {
        return abortExecution(false);
    }

    @Override
    public void abortAllAndFail(Throwable t) {
        lockCoordinator.assertHasStateLock();
        failures.add(t);
        abortExecution(true);
    }

    @Override
    public void cancelExecution() {
        lockCoordinator.assertHasStateLock();
        buildCancelled = abortExecution() || buildCancelled;
    }

    private boolean abortExecution(boolean abortAll) {
        boolean aborted = false;
        executionQueue.restart();
        while (executionQueue.hasNext()) {
            Node node = executionQueue.next();
            if (abortAll || node.isCanCancel()) {
                // Allow currently executing and enforced tasks to complete, but skip everything else.
                // If abortAll is set, also stop everything.
                node.cancelExecution(this::recordNodeCompleted);
                executionQueue.remove();
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
        failures.addAll(this.failures);
        if (buildCancelled && failures.isEmpty()) {
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

    static class NodeMapping extends AbstractCollection<Node> {
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

    static class ExecutionQueue {
        private final List<Node> nodes = new ArrayList<>();
        private int nextPos = 0;
        private int insertPos;

        public void setNodes(Collection<Node> nodes) {
            this.nodes.clear();
            this.nodes.addAll(nodes);
            nextPos = 0;
        }

        public void clear() {
            nodes.clear();
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }

        public int size() {
            return nodes.size();
        }

        public void restart() {
            nextPos = 0;
        }

        public boolean hasNext() {
            return nextPos < nodes.size();
        }

        /**
         * Move to the next node.
         */
        public Node next() {
            return nodes.get(nextPos++);
        }

        /**
         * Remove the current node.
         */
        public void remove() {
            nextPos--;
            nodes.remove(nextPos);
        }

        /**
         * Move the given node to the front of the queue. Leave the current node unchanged.
         */
        public void priorityNode(Node node) {
            int previousPos = nodes.indexOf(node);
            nodes.remove(previousPos);
            nodes.add(0, node);
            if (previousPos >= nextPos) {
                nextPos++;
            }
        }

        /**
         * Start inserting nodes before the current node.
         */
        public void startInsert() {
            if (nextPos > 0) {
                nextPos--;
            }
            insertPos = nextPos;
        }

        /**
         * Insert the given node at the current insert position.
         */
        public void insert(Node node) {
            nodes.add(nextPos, node);
            nextPos++;
        }

        /**
         * Finish inserting nodes and make the first inserted node the next node.
         */
        public boolean restartFromInsertPoint() {
            if (nextPos > insertPos) {
                nextPos = insertPos;
                return true;
            } else {
                nextPos++;
                return false;
            }
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder("ExecutionQueue[");
            for (int i = 0; i < nodes.size(); ++i) {
                if (i > 0) {
                    str.append(", ");
                }
                if (i == pos) {
                    str.append('*');
                }
                str.append(nodes.get(i));
            }
            str.append("]");
            return str.toString();
        }
    }
}
