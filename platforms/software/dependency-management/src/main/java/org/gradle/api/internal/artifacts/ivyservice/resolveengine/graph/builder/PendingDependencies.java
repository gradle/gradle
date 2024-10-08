/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks hard (non-constraint) dependencies targeting a given module. A module should end up in a graph if it has
 * hard dependencies. Also tracks all constraints that have been observed for a module. These constraints should be
 * activated when the hard edge count becomes positive.
 */
public class PendingDependencies {
    private final ModuleIdentifier moduleIdentifier;
    private final Set<NodeState> constraintProvidingNodes;
    private int hardEdges;
    private boolean reportActivePending;

    PendingDependencies(ModuleIdentifier moduleIdentifier) {
        this.moduleIdentifier = moduleIdentifier;
        this.constraintProvidingNodes = new LinkedHashSet<>();
        this.hardEdges = 0;
        this.reportActivePending = true;
    }

    void registerConstraintProvider(NodeState nodeState) {
        if (hardEdges != 0) {
            throw new IllegalStateException("Cannot add a pending node for a dependency which is not pending");
        }
        constraintProvidingNodes.add(nodeState);
        if (nodeState.getComponent().getModule().isVirtualPlatform()) {
            reportActivePending = false;
        }
    }

    public void unregisterConstraintProvider(NodeState nodeState) {
        if (hardEdges != 0) {
            throw new IllegalStateException("Cannot remove a pending node for a dependency which is not pending");
        }
        constraintProvidingNodes.remove(nodeState);
    }

    void turnIntoHardDependencies() {
        for (NodeState affectedComponent : constraintProvidingNodes) {
            affectedComponent.prepareForConstraintNoLongerPending(moduleIdentifier);
        }
        constraintProvidingNodes.clear();
        reportActivePending = true;
    }

    /**
     * Return true iff all nodes in this module have no non-constraint edges
     */
    public boolean isPending() {
        return hardEdges == 0;
    }

    boolean hasConstraintProviders() {
        return !constraintProvidingNodes.isEmpty();
    }

    void increaseHardEdgeCount() {
        hardEdges++;
    }

    void decreaseHardEdgeCount() {
        assert hardEdges > 0 : "Cannot remove a hard edge when none recorded";
        hardEdges--;
    }

    public boolean shouldReportActivatePending() {
        return reportActivePending;
    }

    public void retarget(PendingDependencies pendingDependencies) {
        hardEdges += pendingDependencies.hardEdges;
    }
}
