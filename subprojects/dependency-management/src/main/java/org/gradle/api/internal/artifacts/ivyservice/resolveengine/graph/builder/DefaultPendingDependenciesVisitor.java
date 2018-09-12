/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.List;

class DefaultPendingDependenciesVisitor implements PendingDependenciesVisitor {

    private final ResolveState resolveState;
    private List<PendingDependencies> noLongerPending;

    DefaultPendingDependenciesVisitor(ResolveState resolveState) {
        this.resolveState = resolveState;
    }

    @Override
    public boolean maybeAddAsPendingDependency(NodeState node, DependencyState dependencyState) {
        ModuleIdentifier key = dependencyState.getModuleIdentifier();
        boolean isConstraint = dependencyState.getDependency().isConstraint();
        if (!isConstraint) {
            markNotPending(key);
            return false;
        }

        // Adding an optional dependency: see if we already have a hard dependency on the same module
        ModuleResolveState module = resolveState.getModule(key);
        boolean pending = module.isPending();

        // Already have a hard dependency, this optional dependency is not pending.
        if (!pending) {
            return false;
        }

        // No hard dependency, queue up pending dependency in case we see a hard dependency later.
        module.addPendingNode(node);
        return true;
    }

    @Override
    public void markNotPending(ModuleIdentifier id) {
        markNoLongerPending(resolveState.getModule(id).getPendingDependencies());
    }

    private void markNoLongerPending(PendingDependencies pendingDependencies) {
        if (pendingDependencies.hasPendingComponents()) {
            if (noLongerPending == null) {
                noLongerPending = Lists.newLinkedList();
            }
            noLongerPending.add(pendingDependencies);
        }
        pendingDependencies.increaseHardEdgeCount();
    }

    @Override
    public void complete() {
        if (noLongerPending != null) {
            for (PendingDependencies pendingDependencies : noLongerPending) {
                pendingDependencies.turnIntoHardDependencies();
            }
            noLongerPending = null;
        }
    }
}
