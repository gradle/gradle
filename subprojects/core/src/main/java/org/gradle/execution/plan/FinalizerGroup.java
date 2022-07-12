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

import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
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
    private final TaskNode node;
    private final NodeGroup delegate;
    private final Set<Node> members = new LinkedHashSet<>();
    @Nullable
    private OrdinalGroup ordinal;
    @Nullable
    private ElementSuccessors successors;

    public FinalizerGroup(TaskNode node, NodeGroup delegate) {
        this.ordinal = delegate.asOrdinal();
        this.node = node;
        this.delegate = delegate;
    }

    @Override
    public String toString() {
        return "finalizer " + node + " in " + ordinal;
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

    @Override
    public Collection<Node> getSuccessorsFor(Node node) {
        assert members.contains(node) : "Node " + node + " is not part of the finalizer group of " + this.node;
        if (successors == null) {
            successors = createSuccessors();
        }
        return successors.successorsFor(node);
    }

    @Override
    public Collection<Node> getSuccessorsInReverseOrderFor(Node node) {
        List<Node> successors = new ArrayList<>(getSuccessorsFor(node));
        Collections.reverse(successors);
        return successors;
    }

    private ElementSuccessors createSuccessors() {
        for (Node finalizedNode : getFinalizedNodes()) {
            if (members.contains(finalizedNode)) {
                return new FinalizesMembers();
            }
        }
        return new DoesNotFinalizeMembers();
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
        members.add(node);
        delegate.addMember(node);
        successors = null;
    }

    @Override
    public void removeMember(Node node) {
        members.remove(node);
        delegate.removeMember(node);
        successors = null;
    }

    public void visitAllMembers(Consumer<Node> visitor) {
        for (Node member : members) {
            visitor.accept(member);
        }
    }

    @Override
    public Node.DependenciesState checkSuccessorsCompleteFor(Node node) {
        if (!isFinalizerNode(node) && memberCanStartAtAnyTime(node)) {
            // Is not the finalizer and is reachable from an entry point, so can start
            return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        }

        // Wait for all finalized nodes, potentially excluding some finalized nodes that are also members of this group
        Collection<Node> successors = getSuccessorsFor(node);
        if (successors.isEmpty()) {
            // All finalized nodes are also members and none can start early
            return checkPeersCompleteFor(node);
        }
        boolean isAnyExecuted = false;
        for (Node finalized : successors) {
            if (!finalized.isComplete()) {
                return Node.DependenciesState.NOT_COMPLETE;
            }
            isAnyExecuted |= finalized.isExecuted();
        }
        // All relevant finalized nodes have completed
        // Can run if any finalized node executed or if this node is reachable from an entry point
        if (isAnyExecuted || delegate.isReachableFromEntryPoint()) {
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

    /**
     * Determines the state for a member node that is also a finalized node, in the case where all finalized nodes are also member nodes
     * and none can start for some other reason.
     *
     * The approach is to defer execution until one of the member nodes is "activated", that is, it can start if its membership in this
     * group is ignored.
     */
    private Node.DependenciesState checkPeersCompleteFor(Node node) {
        // If some other member has already completed, then allow all other members to start.
        for (Node member : members) {
            if (member.isComplete()) {
                return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
            }
        }

        HasFinalizers hasFinalizers = (HasFinalizers) node.getGroup();
        for (FinalizerGroup group : hasFinalizers.getFinalizerGroups()) {
            if (group == this) {
                continue;
            }
            if (group.isActivated(node)) {
                return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
            }
        }

        return Node.DependenciesState.NOT_COMPLETE;
    }

    private boolean isActivated(Node node) {
        // A node is active when it is the finalizer of this group and is ready to start
        if (node == this.node) {
            return checkSuccessorsCompleteFor(node) == Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        }
        return false;
    }

    private boolean isFinalizerNode(Node node) {
        return node == this.node;
    }

    private abstract class ElementSuccessors {
        public Set<Node> successorsFor(Node node) {
            // If the node is the finalizer for this group, it should wait for all the finalized nodes
            if (isFinalizerNode(node)) {
                return getFinalizedNodes();
            }

            // If the node is reachable from an entry point, it can start at any time
            if (memberCanStartAtAnyTime(node)) {
                return Collections.emptySet();
            }

            return getFilteredSuccessorsFor(node);
        }

        protected abstract Set<Node> getFilteredSuccessorsFor(Node node);
    }

    private class DoesNotFinalizeMembers extends ElementSuccessors {
        @Override
        protected Set<Node> getFilteredSuccessorsFor(Node node) {
            // None of the finalized nodes are members of the group -> so all members should wait for all the finalized nodes
            return getFinalizedNodes();
        }
    }

    private class FinalizesMembers extends ElementSuccessors {
        private final Set<Node> finalizedNodesThatCanStartAtAnyTime;
        private final Set<Node> membersThatAreDeferred;

        public FinalizesMembers() {
            Set<Node> successors = getFinalizedNodes();
            finalizedNodesThatCanStartAtAnyTime = new LinkedHashSet<>(successors.size());
            List<Node> queue = new ArrayList<>();
            for (Node successor : successors) {
                if (!members.contains(successor)) {
                    finalizedNodesThatCanStartAtAnyTime.add(successor);
                } else if (memberCanStartAtAnyTime(successor)) {
                    finalizedNodesThatCanStartAtAnyTime.add(successor);
                } else {
                    queue.add(successor);
                }
            }

            // TODO - a better option would be to split FinalizerGroup into several types, eg so that there are implementations for members that should start
            // early, those that should be deferred, etc. Then the general purpose inheritance mechanism can be used instead of traversing the dependencies here
            membersThatAreDeferred = new HashSet<>();
            while (!queue.isEmpty()) {
                Node node = queue.remove(0);
                if (!membersThatAreDeferred.add(node)) {
                    continue;
                }
                queue.addAll(0, node.getDependencySuccessors());
            }
        }

        @Override
        protected Set<Node> getFilteredSuccessorsFor(Node node) {
            // If the node is not finalized by this group,  it should wait for all the finalized nodes
            if (!membersThatAreDeferred.contains(node)) {
                return getFinalizedNodes();
            }

            // The node is also finalized by this group, so it should wait for those finalized nodes that can start at any time
            return finalizedNodesThatCanStartAtAnyTime;
        }
    }

}
