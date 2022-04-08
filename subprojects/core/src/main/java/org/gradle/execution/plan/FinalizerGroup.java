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
    @Nullable
    private OrdinalGroup ordinal;

    public FinalizerGroup(TaskNode node, NodeGroup delegate) {
        this.ordinal = delegate.asOrdinal();
        this.node = node;
        this.delegate = delegate;
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

    public NodeGroup getDelegate() {
        return delegate;
    }

    public Collection<Node> getFinalizedNodes() {
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
    public Collection<Node> getSuccessors() {
        return node.getFinalizingSuccessors();
    }

    @Override
    public Collection<Node> getSuccessorsInReverseOrder() {
        return node.getFinalizingSuccessorsInReverseOrder();
    }

    public void maybeInheritFrom(NodeGroup fromGroup) {
        OrdinalGroup ordinal = fromGroup.asOrdinal();
        if (ordinal != null && (this.ordinal == null || this.ordinal.getOrdinal() < ordinal.getOrdinal())) {
            this.ordinal = ordinal;
        }
    }

    @Override
    public Set<FinalizerGroup> getFinalizerGroups() {
        return ImmutableSet.of(this);
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
        if (delegate.isReachableFromEntryPoint() && node != this.node) {
            return Node.DependenciesState.COMPLETE_AND_SUCCESSFUL;
        }

        // Otherwise, wait for all finalized nodes to complete
        boolean isAnyExecuted = false;
        for (Node finalized : getFinalizedNodes()) {
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
}
