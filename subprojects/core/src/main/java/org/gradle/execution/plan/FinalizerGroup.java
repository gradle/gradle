/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The set of nodes reachable from a particular finalizer node.
 */
public class FinalizerGroup extends HasFinalizers {
    private static final MemberSuccessors DO_NOT_BLOCK = new DoNotBlock();
    private final TaskNode node;
    private final NodeGroup delegate;
    private final Set<Node> members = new LinkedHashSet<>();
    @Nullable
    private OrdinalGroup ordinal;
    @Nullable
    private ElementSuccessors successors;
    private boolean finalizedNodeHasStarted;

    public FinalizerGroup(TaskNode node, NodeGroup delegate) {
        this.ordinal = delegate.asOrdinal();
        this.node = node;
        this.delegate = delegate;
    }

    @Override
    public String toString() {
        return "finalizer " + node + " ordinal: " + ordinal + ", delegate: " + delegate;
    }

    public TaskNode getNode() {
        return node;
    }

    @Override
    public NodeGroup getOrdinalGroup() {
        return delegate;
    }

    @Override
    public NodeGroup withOrdinalGroup(OrdinalGroup newOrdinal) {
        ordinal = newOrdinal;
        return this;
    }

    @Override
    public NodeGroup reachableFrom(OrdinalGroup newOrdinal) {
        ordinal = newOrdinal;
        return this;
    }

    public NodeGroup getDelegate() {
        return delegate;
    }

    /**
     * Returns a set of nodes that are finalized by this group. The returned set might contain the nodes belonging to this group.
     */
    public Set<Node> getFinalizedNodes() {
        return node.getFinalizingSuccessors();
    }

    @Nullable
    @Override
    public OrdinalGroup asOrdinal() {
        return ordinal;
    }

    @Override
    public boolean isReachableFromEntryPoint() {
        return delegate.isReachableFromEntryPoint();
    }

    @Nullable
    @Override
    public FinalizerGroup asFinalizer() {
        return this;
    }

    private static boolean memberCanStartAtAnyTime(Node node) {
        return node.getGroup().isReachableFromEntryPoint();
    }

    @Override
    public Set<FinalizerGroup> getFinalizerGroups() {
        return ImmutableSet.of(this);
    }

    @Override
    public void addMember(Node node) {
        assert successors == null;
        members.add(node);
        delegate.addMember(node);
    }

    @Override
    public void removeMember(Node node) {
        assert successors == null;
        members.remove(node);
        delegate.removeMember(node);
    }

    public void visitAllMembers(Consumer<Node> visitor) {
        for (Node member : members) {
            visitor.accept(member);
        }
    }

    @Override
    public boolean isCanCancel() {
        if (!isCanCancel(Collections.singletonList(this))) {
            return false;
        } else {
            return delegate.isCanCancel();
        }
    }

    public boolean isCanCancelSelf() {
        if (node.allDependenciesComplete() && !node.allDependenciesSuccessful()) {
            // Finalizer won't run, so there's no point running its dependencies
            return true;
        }
        // Don't cancel if any finalized node has started
        return !finalizedNodeHasStarted;
    }

    @Override
    public void onNodeStart(Node finalizer, Node node) {
        if (isFinalizerNode(finalizer) && !finalizedNodeHasStarted && getFinalizedNodes().contains(node)) {
            finalizedNodeHasStarted = true;
        }
    }

    public void scheduleMembers(SetMultimap<FinalizerGroup, FinalizerGroup> reachableGroups) {
        Set<Node> finalizedNodesToBlockOn = findFinalizedNodesThatDoNotIntroduceACycle(reachableGroups);
        WaitForNodesToComplete waitForFinalizers = new WaitForNodesToComplete(finalizedNodesToBlockOn);

        // Determine the finalized nodes that are also members. These may need to block waiting for other finalized nodes to complete
        Set<Node> blockedFinalizedMembers = new HashSet<>(getFinalizedNodes());
        blockedFinalizedMembers.removeAll(finalizedNodesToBlockOn);
        blockedFinalizedMembers.retainAll(members);

        if (blockedFinalizedMembers.isEmpty()) {
            // When there are no members that are also finalized, then all members are blocked by the finalizer nodes that don't introduce a cycle
            successors = node -> waitForFinalizers;
            return;
        }

        // There are some finalized nodes that are also members
        // For each member, determine which finalized nodes to wait for
        ImmutableMap.Builder<Node, MemberSuccessors> blockingNodesBuilder = ImmutableMap.builder();

        // Calculate the set of dependencies of finalized nodes that are also members of this group
        Set<Node> dependenciesThatAreMembers = getDependenciesThatAreMembers(blockedFinalizedMembers);

        for (Node member : members) {
            if (isFinalizerNode(member) || memberCanStartAtAnyTime(member)) {
                // Short-circuit for these, they are handled separately
                continue;
            }
            if (blockedFinalizedMembers.contains(member)) {
                if (!finalizedNodesToBlockOn.isEmpty()) {
                    // This member is finalized and there are some finalized nodes that are not members. Wait for those nodes
                    blockingNodesBuilder.put(member, waitForFinalizers);
                } else {
                    // All finalized nodes are also members. Block until some other finalized node is started
                    blockingNodesBuilder.put(member, new WaitForFinalizedNodesToBecomeActive(Collections.singleton(member)));
                }
            } else {
                if (dependenciesThatAreMembers.contains(member)) {
                    // This member is a dependency of a finalized member. Treat is as if it were a finalized member.
                    blockingNodesBuilder.put(member, waitForFinalizers);
                } else {
                    // Wait for the finalized nodes that don't introduce a cycle
                    Set<Node> blockOn = new LinkedHashSet<>(finalizedNodesToBlockOn);
                    blockOn.addAll(blockedFinalizedMembers);
                    blockingNodesBuilder.put(member, new WaitForNodesToComplete(blockOn));
                }
            }
        }
        ImmutableMap<Node, MemberSuccessors> blockingNodes = blockingNodesBuilder.build();
        successors = blockingNodes::get;
    }

    private Set<Node> findFinalizedNodesThatDoNotIntroduceACycle(SetMultimap<FinalizerGroup, FinalizerGroup> reachableGroups) {
        // The members of this group have an implicit dependency on each finalized node of this group.
        // When a finalizer node is a member of some other group, then it in turn has an implicit dependency on the finalized nodes of that group.
        // This can introduce a cycle. So, determine all the groups reachable from the finalizers of this group via these relationships
        // and check for cycles
        Set<Node> nodesWithNoCycle = new HashSet<>(getFinalizedNodes().size());
        for (Node finalizedNode : getFinalizedNodes()) {
            if (!hasACycle(finalizedNode, reachableGroups)) {
                nodesWithNoCycle.add(finalizedNode);
            }
        }
        return nodesWithNoCycle;
    }

    @Override
    public Node.DependenciesState checkSuccessorsCompleteFor(Node node) {
        MemberSuccessors waitingFor = getNodesThatBlock(node);
        Node.DependenciesState state = waitingFor.successorsComplete();
        if (state != null) {
            return state;
        }

        // All relevant finalized nodes have completed but none have executed
        // Can run the finalized node is reachable from an entry point
        if (delegate.isReachableFromEntryPoint()) {
            return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        }

        // All finalized nodes are complete but none executed
        if (delegate instanceof HasFinalizers) {
            // Wait for upstream finalizers
            return delegate.checkSuccessorsCompleteFor(node);
        } else {
            // Can skip execution
            return Node.DependenciesState.COMPLETE_AND_CAN_SKIP;
        }
    }

    private MemberSuccessors getNodesThatBlock(Node node) {
        if (isFinalizerNode(node)) {
            return new WaitForNodesToComplete(getFinalizedNodes());
        }
        if (memberCanStartAtAnyTime(node)) {
            return DO_NOT_BLOCK;
        }
        return successors.getNodesThatBlock(node);
    }

    private Set<Node> getDependenciesThatAreMembers(Set<Node> blockedFinalizedMembers) {
        Set<Node> dependenciesThatAreMembers = new HashSet<>(members.size());
        Set<Node> seen = new HashSet<>(1024);

        for (Node fromNode : blockedFinalizedMembers) {
            List<Node> queue = new ArrayList<>(1024);
            fromNode.visitHardSuccessors(queue::add);
            while (!queue.isEmpty()) {
                Node toNode = queue.remove(0);
                if (!seen.add(toNode)) {
                    continue;
                }
                if (members.contains(toNode) && !blockedFinalizedMembers.contains(toNode)) {
                    dependenciesThatAreMembers.add(toNode);
                }
                toNode.visitHardSuccessors(queue::add);
            }
        }
        return dependenciesThatAreMembers;
    }

    private boolean dependsOn(Node fromNode, Node toNode) {

        return false;
    }

    private boolean hasACycle(Node finalized, SetMultimap<FinalizerGroup, FinalizerGroup> reachableGroups) {
        if (!(finalized.getGroup() instanceof HasFinalizers) || finalized.getGroup().isReachableFromEntryPoint()) {
            // Is not a member of a finalizer group or will not be blocked
            return false;
        }
        HasFinalizers groups = (HasFinalizers) finalized.getGroup();
        for (FinalizerGroup finalizerGroup : groups.getFinalizerGroups()) {
            if (reachableGroups(finalizerGroup, reachableGroups).contains(this)) {
                return true;
            }
        }
        return false;
    }

    private Set<FinalizerGroup> reachableGroups(FinalizerGroup fromGroup, SetMultimap<FinalizerGroup, FinalizerGroup> reachableGroups) {
        if (!reachableGroups.containsKey(fromGroup)) {
            Set<Node> seen = new HashSet<>();
            List<Node> queue = new ArrayList<>(fromGroup.getFinalizedNodes());
            while (!queue.isEmpty()) {
                Node node = queue.remove(0);
                if (!seen.add(node)) {
                    continue;
                }
                if (node.getGroup().isReachableFromEntryPoint()) {
                    continue;
                }
                if (node.getGroup() instanceof HasFinalizers) {
                    HasFinalizers groups = (HasFinalizers) node.getGroup();
                    for (FinalizerGroup finalizerGroup : groups.getFinalizerGroups()) {
                        reachableGroups.put(fromGroup, finalizerGroup);
                        queue.addAll(finalizerGroup.getFinalizedNodes());
                    }
                }
                Iterables.addAll(queue, node.getHardSuccessors());
            }
        }
        return reachableGroups.get(fromGroup);
    }

    private boolean isFinalizerNode(Node node) {
        return node == this.node;
    }

    private interface ElementSuccessors {
        MemberSuccessors getNodesThatBlock(Node node);
    }

    private interface MemberSuccessors {
        /**
         * @return null when all successors have completed but none have executed
         */
        @Nullable
        Node.DependenciesState successorsComplete();
    }

    private static class DoNotBlock implements MemberSuccessors {
        @Override
        public Node.DependenciesState successorsComplete() {
            return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        }
    }

    private static class WaitForNodesToComplete implements MemberSuccessors {
        private final Set<Node> nodes;

        public WaitForNodesToComplete(Set<Node> nodes) {
            this.nodes = nodes;
        }

        @Nullable
        @Override
        public Node.DependenciesState successorsComplete() {
            boolean isAnyExecuted = false;
            for (Node node : nodes) {
                if (!node.isComplete()) {
                    return Node.DependenciesState.NOT_COMPLETE;
                }
                isAnyExecuted |= node.isExecuted();
            }
            // All relevant finalized nodes have completed
            if (isAnyExecuted) {
                return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
            }
            return null;
        }
    }

    private static class WaitForFinalizedNodesToBecomeActive implements MemberSuccessors {
        private final Set<Node> nodes;

        public WaitForFinalizedNodesToBecomeActive(Set<Node> nodes) {
            this.nodes = nodes;
        }

        @Nullable
        @Override
        public Node.DependenciesState successorsComplete() {
            for (Node node : nodes) {
                for (FinalizerGroup finalizerGroup : ((HasFinalizers) node.getGroup()).getFinalizerGroups()) {
                    if (finalizerGroup.finalizedNodeHasStarted) {
                        return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
                    }
                }
            }
            return Node.DependenciesState.NOT_COMPLETE;
        }
    }
}
