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

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.Set;

public class PendingDependencies {
    private final ModuleIdentifier moduleIdentifier;
    private final Set<NodeState> affectedComponents;
    private int hardEdges;
    private boolean reportActivePending;

    PendingDependencies(ModuleIdentifier moduleIdentifier) {
        this.moduleIdentifier = moduleIdentifier;
        this.affectedComponents = Sets.newLinkedHashSet();
        this.hardEdges = 0;
        this.reportActivePending = true;
    }

    void addNode(NodeState nodeState) {
        if (hardEdges != 0) {
            throw new IllegalStateException("Cannot add a pending node for a dependency which is not pending");
        }
        affectedComponents.add(nodeState);
        if (nodeState.getComponent().getModule().isVirtualPlatform()) {
            reportActivePending = false;
        }
    }

    public void removeNode(NodeState nodeState) {
        if (hardEdges != 0) {
            throw new IllegalStateException("Cannot remove a pending node for a dependency which is not pending");
        }
        boolean removed = affectedComponents.remove(nodeState);
        assert removed : "Removed a pending node that was not registered";
    }

    void turnIntoHardDependencies() {
        for (NodeState affectedComponent : affectedComponents) {
            affectedComponent.prepareForConstraintNoLongerPending(moduleIdentifier);
        }
        affectedComponents.clear();
        reportActivePending = true;
    }

    public boolean isPending() {
        return hardEdges == 0;
    }

    boolean hasPendingComponents() {
        return !affectedComponents.isEmpty();
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

}
