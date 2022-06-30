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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The set of nodes reachable from a particular finalizer node.
 */
public class FinalizerGroup extends HasFinalizers {
    private final TaskNode node;
    private final NodeGroup delegate;
    private final Set<Node> members = new LinkedHashSet<>();
    /**
     * A collection of Nodes owned by this group. Only a single FinalizerGroup may own a node.
     * The owned nodes can still be members of other finalizer groups though.
     * The important distinction is how these members are handled when they are finalized by this group.
     * They aren't considered the group successors but only successors to the finalizer node itself.
     * This prevents deadlocks and false cycles when a dependency of a finalizer is finalized by it.
     */
    private final Set<Node> ownedMembers = new HashSet<>();
    @Nullable
    private OrdinalGroup ordinal;

    public FinalizerGroup(TaskNode node, NodeGroup delegate) {
        this.ordinal = delegate.asOrdinal();
        this.node = node;
        this.delegate = delegate;
    }

    public FinalizerGroup(TaskNode node, NodeGroup delegate, Collection<Node> ownedMembers) {
        this(node, delegate);
        this.ownedMembers.addAll(ownedMembers);
    }

    @Override
    public String toString() {
        return "finalizer in " + ordinal;
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

    private Set<Node> getFinalizedNodesInReverseOrder() {
        return node.getFinalizingSuccessorsInReverseOrder();
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
        Set<Node> successors = getFinalizedNodes();
        if (!isFinalizerNode(node)) {
            successors = filterSuccessorsForFinalizerDependency(successors);
        }
        return successors;
    }

    @Override
    public Collection<Node> getSuccessorsInReverseOrderFor(Node node) {
        assert members.contains(node) : "Node " + node + " is not part of the finalizer group of " + this.node;
        Set<Node> successors = getFinalizedNodesInReverseOrder();
        if (!isFinalizerNode(node)) {
            successors = filterSuccessorsForFinalizerDependency(successors);
        }
        return successors;
    }

    private Set<Node> filterSuccessorsForFinalizerDependency(Set<Node> successors) {
        // The nodes that are dependencies of the finalizer can have a smaller set of successors.
        // Owned nodes finalized by this finalizer that are also dependencies of the finalizer are not considered
        // successors to finalizer dependencies (but they are successors to the finalizer).
        // This prevents deadlocks and false cycles when two unrelated finalizer's dependencies finalized by this
        // finalizer are considered each other's successors.
        // Such nodes are still successors to other finalized nodes, like non-members and not-owned members.
        // Not owned members are expected to be scheduled by their other group. Typically, it is a node reachable
        // from the entry point, but a dependency of some other finalizer can also be an example.
        Set<Node> filteredSuccessors = new LinkedHashSet<>(successors);
        filteredSuccessors.removeIf(ownedMembers::contains);
        return filteredSuccessors;
    }

    @Override
    public Set<FinalizerGroup> getFinalizerGroups() {
        return ImmutableSet.of(this);
    }

    @Override
    public void maybeAddToOwnedMembers(Node node) {
        ownedMembers.add(node);
    }

    @Override
    public void removeFromOwnedMembers(Node node) {
        ownedMembers.remove(node);
    }

    public Collection<Node> getOwnedMembers() {
        return Collections.unmodifiableCollection(ownedMembers);
    }

    @Override
    public void addMember(Node node) {
        members.add(node);
        delegate.addMember(node);
    }

    @Override
    public void removeMember(Node node) {
        members.remove(node);
        delegate.removeMember(node);
    }

    public void visitAllMembers(Consumer<Node> visitor) {
        for (Node member : members) {
            visitor.accept(member);
        }
    }

    @Override
    public Node.DependenciesState checkSuccessorsCompleteFor(Node node) {
        // If this node is reachable from an entry point and is not the finalizer itself, then it can start at any time
        if (delegate.isReachableFromEntryPoint() && !isFinalizerNode(node)) {
            return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        }

        // Otherwise, wait for (almost) all finalized nodes to complete with one exception.
        boolean isAnyExecuted = false;
        for (Node finalized : getSuccessorsFor(node)) {
            if (!finalized.isComplete()) {
                return Node.DependenciesState.NOT_COMPLETE;
            }
            isAnyExecuted |= finalized.isExecuted();
        }
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

    private boolean isFinalizerNode(Node node) {
        return node == this.node;
    }
}
