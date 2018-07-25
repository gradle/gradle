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

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.List;

/**
 * This class is responsible for maintaining the state of pending dependencies. In other words, when such a dependency (e.g. a dependency constraints or a maven optional dependency), is added to the
 * graph, it is "pending" until a hard dependency for the same module is seen. As soon as a hard dependency is found, nodes that referred to the pending dependency are restarted.
 */
class DefaultPendingDependenciesHandler implements PendingDependenciesHandler {
    private final PendingDependenciesState pendingDependencies = new PendingDependenciesState();

    @Override
    public Visitor start() {
        return new DefaultVisitor();
    }

    public class DefaultVisitor implements Visitor {
        private List<PendingDependencies> noLongerPending;

        public boolean maybeAddAsPendingDependency(NodeState node, DependencyState dependencyState) {
            ModuleIdentifier key = dependencyState.getModuleIdentifier();
            boolean isOptionalDependency = dependencyState.getDependency().isPending();
            if (!isOptionalDependency) {
                // Mark as not pending. If we saw pending dependencies before, mark them as no longer pending
                PendingDependencies priorPendingDependencies = pendingDependencies.notPending(key);
                if (priorPendingDependencies != null) {
                    if (noLongerPending == null) {
                        noLongerPending = Lists.newLinkedList();
                    }
                    noLongerPending.add(priorPendingDependencies);
                }
                return false;
            }

            // Adding an optional dependency: see if we already have a hard dependency on the same module
            PendingDependencies pendingDependencies = DefaultPendingDependenciesHandler.this.pendingDependencies.getPendingDependencies(key);
            boolean pending = pendingDependencies.isPending();

            // Already have a hard dependency, this optional dependency is not pending.
            if (!pending) {
                return false;
            }

            // No hard dependency, queue up pending dependency in case we see a hard dependency later.
            pendingDependencies.addNode(node);
            return true;
        }

        public void complete() {
            if (noLongerPending != null) {
                for (PendingDependencies pendingDependencies : noLongerPending) {
                    pendingDependencies.turnIntoHardDependencies();
                }
                noLongerPending = null;
            }
        }
    }
}
