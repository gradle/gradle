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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Action;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.NonNullApi;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.Pair;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.google.common.collect.Sets.newIdentityHashSet;
import static java.lang.String.format;

@NonNullApi
public class DefaultFinalizedExecutionPlan implements WorkSource<Node>, FinalizedExecutionPlan {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFinalizedExecutionPlan.class);
    public static final Comparator<Node> NODE_EXECUTION_ORDER = new Comparator<Node>() {
        @Override
        public int compare(Node node1, Node node2) {
            if (node1.isPriority() && !node2.isPriority()) {
                return -1;
            } else if (!node1.isPriority() && node2.isPriority()) {
                return 1;
            }
            if (node1.getIndex() > node2.getIndex()) {
                return 1;
            } else if (node1.getIndex() < node2.getIndex()) {
                return -1;
            }
            return NodeComparator.INSTANCE.compare(node1, node2);
        }
    };

    private final Set<Node> waitingToStartNodes = new HashSet<>();
    private final ExecutionQueue readyNodes = new ExecutionQueue();
    private final List<Throwable> failures = new ArrayList<>();
    private final List<DiagnosticEvent> diagnosticEvents = new ArrayList<>();
    private final String displayName;
    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchy destroyableHierarchy;
    private final ResourceLockCoordinationService lockCoordinator;
    private final Action<ResourceLock> resourceUnlockListener = this::resourceUnlocked;

    private boolean invalidNodeRunning;
    private final boolean continueOnFailure;
    private final QueryableExecutionPlan contents;

    private final Set<Node> runningNodes = newIdentityHashSet();
    private final Map<Pair<Node, Node>, Boolean> reachableCache = new HashMap<>();
    private final OrdinalNodeAccess ordinalNodeAccess;
    private final Consumer<LocalTaskNode> completionHandler;

    // When true, there may be nodes that are both ready and "selectable", which means their project and resources are able to be locked
    // When false, there are definitely no nodes that are "selectable"
    private boolean maybeNodesSelectable;

    private boolean buildCancelled;

    public DefaultFinalizedExecutionPlan(
        String displayName,
        OrdinalNodeAccess ordinalNodeAccess,
        ExecutionNodeAccessHierarchy outputHierarchy,
        ExecutionNodeAccessHierarchy destroyableHierarchy,
        ResourceLockCoordinationService lockCoordinator,
        List<Node> scheduledNodes,
        boolean continueOnFailure,
        QueryableExecutionPlan contents,
        Consumer<LocalTaskNode> completionHandler
    ) {
        this.displayName = displayName;
        this.outputHierarchy = outputHierarchy;
        this.destroyableHierarchy = destroyableHierarchy;
        this.lockCoordinator = lockCoordinator;
        this.ordinalNodeAccess = ordinalNodeAccess;
        this.continueOnFailure = continueOnFailure;
        this.contents = contents;
        this.completionHandler = completionHandler;

        SetMultimap<FinalizerGroup, FinalizerGroup> reachableGroups = LinkedHashMultimap.create();
        for (Node node : scheduledNodes) {
            if (node.getFinalizerGroup() != null) {
                node.getFinalizerGroup().scheduleMembers(reachableGroups);
            }
        }

        for (int i = 0; i < scheduledNodes.size(); i++) {
            Node node = scheduledNodes.get(i);
            node.setIndex(i);
            node.prepareForExecution(this::monitoredNodeReady);
            node.updateAllDependenciesComplete();
            maybeNodeReady(node);
            maybeWaitingForNewNode(node, "scheduled");
        }
        lockCoordinator.addLockReleaseListener(resourceUnlockListener);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public QueryableExecutionPlan getContents() {
        return contents;
    }

    @Override
    public WorkSource<Node> asWorkSource() {
        return this;
    }

    @Override
    public void close() {
        lockCoordinator.removeLockReleaseListener(resourceUnlockListener);
        waitingToStartNodes.clear();
        readyNodes.clear();
        runningNodes.clear();
        reachableCache.clear();
    }

    private void resourceUnlocked(ResourceLock resourceLock) {
        if (!(resourceLock instanceof WorkerLeaseRegistry.WorkerLease) && !readyNodes.isEmpty()) {
            maybeNodesSelectable = true;
        }
    }

    @Override
    public State executionState() {
        lockCoordinator.assertHasStateLock();
        if (waitingToStartNodes.isEmpty()) {
            return State.NoMoreWorkToStart;
        } else if (!readyNodes.isEmpty() && maybeNodesSelectable) {
            return State.MaybeWorkReadyToStart;
        } else {
            return State.NoWorkReadyToStart;
        }
    }

    @Override
    public Diagnostics healthDiagnostics() {
        lockCoordinator.assertHasStateLock();

        List<String> ordinalGroups = new ArrayList<>();
        for (OrdinalGroup group : ordinalNodeAccess.getAllGroups()) {
            ordinalGroups.add(group.diagnostics());
        }

        List<String> waitingToStartItems = new ArrayList<>(waitingToStartNodes.size());
        for (Node node : waitingToStartNodes) {
            waitingToStartItems.add(node.healthDiagnostics());
        }
        List<String> readyToStartItems = new ArrayList<>(readyNodes.size());
        for (Node node : readyNodes.nodes) {
            readyToStartItems.add(node.toString());
        }
        List<String> otherWaitingItems = new ArrayList<>();
        visitWaitingNodes(node -> {
            if (!waitingToStartNodes.contains(node)) {
                otherWaitingItems.add(node.healthDiagnostics());
            }
        });
        List<String> eventItems = new ArrayList<>(diagnosticEvents.size());
        for (DiagnosticEvent event : diagnosticEvents) {
            eventItems.add(event.message());
        }

        return new Diagnostics(displayName, ordinalGroups, waitingToStartItems, readyToStartItems, otherWaitingItems, eventItems);
    }

    /**
     * Visits the waiting nodes and their dependencies, dependencies first. Does not visit nodes that have completed.
     */
    private void visitWaitingNodes(Consumer<Node> visitor) {
        List<Node> queue = new ArrayList<>(waitingToStartNodes);
        Set<Node> visited = new HashSet<>();
        Set<Node> visiting = new HashSet<>();
        while (!queue.isEmpty()) {
            Node node = queue.get(0);
            if (node.isComplete() || visited.contains(node)) {
                queue.remove(0);
                continue;
            }
            if (visiting.add(node)) {
                int pos = 0;
                for (Node successor : node.getHardSuccessors()) {
                    queue.add(pos++, successor);
                }
            } else {
                visitor.accept(node);
                visited.add(node);
            }
        }
    }

    @Override
    public Selection<Node> selectNext() {
        lockCoordinator.assertHasStateLock();
        if (waitingToStartNodes.isEmpty()) {
            return Selection.noMoreWorkToStart();
        }
        if (readyNodes.isEmpty() || !maybeNodesSelectable) {
            return Selection.noWorkReadyToStart();
        }

        List<ResourceLock> resources = new ArrayList<>();
        readyNodes.restart();
        while (readyNodes.hasNext()) {
            Node node = readyNodes.next();
            if (node.allDependenciesComplete()) {
                if (!node.allDependenciesSuccessful()) {
                    // Nodes whose dependencies have failed are added to the 'readyNodes' queue.
                    // This is because of history, where all nodes were added to this queue regardless of their status.
                    // Instead, the nodes should be cancelled when a dependent fails and never added to the queue.
                    //
                    // Cannot execute this node due to failed dependencies - skip it
                    if (node.shouldCancelExecutionDueToDependencies()) {
                        node.cancelExecution(this::recordNodeCompleted);
                    } else {
                        node.markFailedDueToDependencies(this::recordNodeCompleted);
                    }
                    // Skipped some nodes, which may invalidate some earlier nodes (for example a shared dependency of multiple finalizers when all finalizers are skipped), so start again
                    readyNodes.removeAndRestart(node);
                    continue;
                }

                if (node.hasPendingPreExecutionNodes()) {
                    // The node is ready to execute and its pre-execution nodes have not been scheduled, so do this now
                    node.visitPreExecutionNodes(prepareNode -> {
                        prepareNode.setIndex(node.getIndex());
                        prepareNode.require();
                        prepareNode.updateAllDependenciesComplete();
                        node.addDependencySuccessor(prepareNode);
                        addNodeToPlan(prepareNode);
                    });
                    node.forceAllDependenciesCompleteUpdate();
                    if (!node.allDependenciesComplete()) {
                        // Some pre-execution nodes were scheduled, so try to execute them now
                        readyNodes.removeAndRestart(node);
                        continue;
                    }
                }

                // Node is ready to execute and all dependencies and pre-execution nodes have completed
                if (attemptToStart(node, resources)) {
                    readyNodes.remove();
                    waitingToStartNodes.remove(node);
                    node.getMutationInfo().started();
                    return Selection.of(node);
                }
            }
            if (node.isComplete()) {
                // Is already complete, for example:
                // - node was cancelled while in the queue
                readyNodes.remove();
            }
        }

        maybeNodesSelectable = false;
        if (waitingToStartNodes.isEmpty()) {
            return Selection.noMoreWorkToStart();
        }
        // No nodes are able to start, for example
        // - they are ready to execute but cannot acquire the resources they need to start
        // - they are waiting for their dependencies to complete
        // - they are waiting for some external event
        // - they are a finalizer for nodes that are not yet complete
        return Selection.noWorkReadyToStart();
    }

    private void addNodeToPlan(Node node) {
        maybeNodeReady(node);
        maybeWaitingForNewNode(node, "runtime");
    }

    private boolean attemptToStart(Node node, List<ResourceLock> resources) {
        resources.clear();
        if (!tryAcquireLocksForNode(node, resources)) {
            releaseLocks(resources);
            return false;
        }

        MutationInfo mutations = node.getMutationInfo();

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
        } else if (mutationConflictsWithOtherNodes(node, mutations)) {
            return true;
        } else if (destroysNotYetConsumedOutputOfAnotherNode(node, mutations.destroyablePaths)) {
            LOGGER.debug("Node {} destroys not yet consumed output of another node", node);
            return true;
        }
        return false;
    }

    private void updateAllDependenciesCompleteForPredecessors(Node node) {
        node.visitAllNodesWaitingForThisNode(dependent -> {
            dependent.onNodeComplete(node);
            maybeNodeReady(dependent);
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

    private boolean canRunWithCurrentlyExecutedNodes(MutationInfo mutations) {
        // No new work should be started when invalid work is running
        if (mutations.hasValidationProblem) {
            // Invalid work is not allowed to run together with any other work
            return runningNodes.isEmpty();
        } else {
            return !invalidNodeRunning;
        }
    }

    private boolean mutationConflictsWithOtherNodes(Node node, MutationInfo mutations) {
        Set<String> nodeOutputPaths = mutations.outputPaths;
        Set<String> nodeDestroysPaths = mutations.destroyablePaths;
        if (nodeOutputPaths.isEmpty() && nodeDestroysPaths.isEmpty()) {
            return false;
        }

        BiFunction<Boolean, Node, Boolean> conflictsWithRunning = (current, candidate) -> current || candidate.isExecuting();

        OrdinalGroup nodeOrdinal = node.getOrdinal();
        BiFunction<Boolean, Node, Boolean> conflictsWithNodeInEarlierOrdinal = (current, candidate) -> {
            if (current || candidate.isComplete()) {
                return current;
            }
            OrdinalGroup otherOrdinal = candidate.getOrdinal();
            return otherOrdinal != null && otherOrdinal.getOrdinal() < nodeOrdinal.getOrdinal();
        };

        for (String path : nodeOutputPaths) {
            if (outputHierarchy.visitNodesAccessing(path, false, conflictsWithRunning)) {
                return true;
            }
            if (nodeOrdinal != null) {
                if (destroyableHierarchy.visitNodesAccessing(path, false, conflictsWithNodeInEarlierOrdinal)) {
                    return true;
                }
            }
        }
        for (String path : nodeDestroysPaths) {
            if (destroyableHierarchy.visitNodesAccessing(path, false, conflictsWithRunning)) {
                return true;
            }
            if (nodeOrdinal != null) {
                if (outputHierarchy.visitNodesAccessing(path, false, conflictsWithNodeInEarlierOrdinal)) {
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

        BiFunction<Boolean, Node, Boolean> conflicts = (current, producingNode) -> {
            if (current) {
                return current;
            }
            if (!producingNode.getMutationInfo().isOutputProducedButNotYetConsumed()) {
                return false;
            }
            MutationInfo producingNodeMutations = producingNode.getMutationInfo();
            for (Node consumer : producingNodeMutations.getNodesYetToConsumeOutput()) {
                if (doesConsumerDependOnDestroyer(consumer, destroyer)) {
                    // If there's an explicit dependency from consuming node to destroyer,
                    // then we accept that as the will of the user
                    continue;
                }
                LOGGER.debug("Node {} destroys output of consumer {}", destroyer, consumer);
                return true;
            }
            return false;
        };

        for (String destroyablePath : destroyablePaths) {
            if (outputHierarchy.visitNodesAccessing(destroyablePath, false, conflicts)) {
                return true;
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
        waitingToStartNodes.remove(node);
        if (continueOnFailure && !node.allDependenciesComplete()) {
            // Wait for any dependencies of this node that have not started yet
            for (Node successor : node.getDependencySuccessors()) {
                if (successor.isRequired()) {
                    waitingForNode(successor, "other node completed", node);
                }
            }
        }

        for (Node producer : node.getDependencySuccessors()) {
            MutationInfo producerMutations = producer.getMutationInfo();
            producerMutations.consumerCompleted(node);
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
        node.updateAllDependenciesComplete();
        maybeNodeReady(node);
    }

    @Override
    public void finishedExecuting(Node node, @Nullable Throwable failure) {
        lockCoordinator.assertHasStateLock();
        try {
            runningNodes.remove(node);

            if (failure != null) {
                node.setExecutionFailure(failure);
            }
            if (!node.isExecuting()) {
                throw new IllegalStateException(format("Cannot finish executing %s as it is in an unexpected state %s.", node, node.getState()));
            }

            if (!readyNodes.isEmpty()) {
                maybeNodesSelectable = true;
            }

            node.finishExecution(this::recordNodeCompleted);
            if (node.isFailed()) {
                LOGGER.debug("Node {} failed", node);
                handleFailure(node);
            } else {
                LOGGER.debug("Node {} finished executing", node);
                node.visitPostExecutionNodes(postNode -> {
                    postNode.setIndex(node.getIndex());
                    postNode.require();
                    postNode.updateAllDependenciesComplete();
                    addNodeToPlan(postNode);
                    for (Node predecessor : node.getDependencyPredecessors()) {
                        predecessor.addDependencySuccessor(postNode);
                        predecessor.forceAllDependenciesCompleteUpdate();
                        if (!predecessor.allDependenciesComplete()) {
                            readyNodes.removeAndRestart(predecessor);
                        }
                    }
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
            maybeNodesSelectable = true;
            readyNodes.insert(node);
        }
    }

    private void maybeWaitingForNewNode(Node node, String whenAdded) {
        // Add some diagnostics to track down sporadic issue
        if (node instanceof OrdinalNode) {
            diagnosticEvents.add(new NodeAdded(node, whenAdded, readyNodes.nodes.contains(node)));
        }
        if (node.getDependencyPredecessors().isEmpty()) {
            waitingForNode(node, whenAdded, null);
        }
    }

    private void waitingForNode(Node node, String whenAdded, @Nullable Node waitingDueTo) {
        // Add some diagnostics to track down sporadic issue
        if (node instanceof OrdinalNode) {
            diagnosticEvents.add(new WaitingForNode(node, waitingDueTo, whenAdded, readyNodes.nodes.contains(node)));
        }
        waitingToStartNodes.add(node);
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
        MutableBoolean cancelled = new MutableBoolean();
        visitWaitingNodes(node -> {
            if (node.isRequired() && (abortAll || node.isCanCancel())) {
                // Allow currently executing and enforced tasks to complete, but skip everything else.
                // If abortAll is set, also stop everything.
                node.cancelExecution(this::recordNodeCompleted);
                cancelled.set(true);
            }
        });
        if (cancelled.get()) {
            maybeNodesSelectable = true;
            return true;
        } else {
            return false;
        }
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
        return waitingToStartNodes.isEmpty() && runningNodes.isEmpty();
    }

    /**
     * An ordered queue of nodes, sorted by {@link #NODE_EXECUTION_ORDER}.
     */
    static class ExecutionQueue {
        private final Set<Node> nodes = new TreeSet<>(NODE_EXECUTION_ORDER);
        private Iterator<Node> current;

        public void clear() {
            nodes.clear();
            current = null;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }

        public int size() {
            return nodes.size();
        }

        public void restart() {
            current = nodes.iterator();
        }

        public boolean hasNext() {
            return current.hasNext();
        }

        /**
         * Move to the next node.
         */
        public Node next() {
            if (current == null) {
                throw new IllegalStateException();
            }
            return current.next();
        }

        /**
         * Remove the current node.
         */
        public void remove() {
            current.remove();
        }

        public void removeAndRestart(Node node) {
            nodes.remove(node);
            restart();
        }

        /**
         * Insert the given node.
         */
        public void insert(Node node) {
            if (nodes.add(node)) {
                current = null;
            }
        }
    }

    private interface DiagnosticEvent {
        String message();
    }

    private static abstract class AbstractNodeEvent implements DiagnosticEvent {
        final Node node;
        final String whenAdded;
        final Node.ExecutionState state;
        final int dependencyCount;
        final boolean readyNode;

        public AbstractNodeEvent(Node node, String whenAdded, boolean readyNode) {
            this.node = node;
            this.whenAdded = whenAdded;
            this.state = node.getState();
            this.dependencyCount = node.getDependencySuccessors().size();
            this.readyNode = readyNode;
        }
    }

    private static class NodeAdded extends AbstractNodeEvent {
        public NodeAdded(Node node, String whenAdded, boolean readyNode) {
            super(node, whenAdded, readyNode);
        }

        @Override
        public String message() {
            return String.format("node added to plan: %s, when: %s, state: %s, dependencies: %s, is ready node? %s", node, whenAdded, state, dependencyCount, readyNode);
        }
    }

    private static class WaitingForNode extends AbstractNodeEvent {
        @Nullable
        private final Node waitingDueTo;

        public WaitingForNode(Node node, @Nullable Node waitingDueTo, String whenAdded, boolean readyNode) {
            super(node, whenAdded, readyNode);
            this.waitingDueTo = waitingDueTo;
        }

        @Override
        public String message() {
            return String.format("node added to waiting-for-set: %s, when: %s, due-to: %s, state: %s, dependencies: %s, is ready node? %s", node, whenAdded, waitingDueTo, state, dependencyCount, readyNode);
        }
    }
}
