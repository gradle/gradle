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
    public PendingState maybeAddAsPendingDependency(NodeState node, DependencyState dependencyState) {
        ModuleIdentifier key = dependencyState.getModuleIdentifier();
        boolean isConstraint = dependencyState.getDependency().isConstraint();
        if (!isConstraint) {
            if (markNotPending(key)) {
                return PendingState.NOT_PENDING_ACTIVATING;
            } else {
                return PendingState.NOT_PENDING;
            }
        }

        // Adding an optional dependency: see if we already have a hard dependency on the same module
        ModuleResolveState module = resolveState.getModule(key);
        boolean pending = module.isPending();

        // Already have a hard dependency, this optional dependency is not pending.
        if (!pending) {
            return PendingState.NOT_PENDING;
        }

        // No hard dependency, queue up pending dependency in case we see a hard dependency later.
        module.registerConstraintProvider(node);
        return PendingState.PENDING;
    }

    @Override
    public boolean markNotPending(ModuleIdentifier id) {
        return markNoLongerPending(resolveState.getModule(id).getPendingDependencies());
    }

    private boolean markNoLongerPending(PendingDependencies pendingDependencies) {
        boolean activatedPending = false;
        if (pendingDependencies.hasConstraintProviders()) {
            if (noLongerPending == null) {
                noLongerPending = Lists.newArrayList();
            }
            noLongerPending.add(pendingDependencies);
            activatedPending = pendingDependencies.shouldReportActivatePending();
        }
        pendingDependencies.increaseHardEdgeCount();
        return activatedPending;
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
