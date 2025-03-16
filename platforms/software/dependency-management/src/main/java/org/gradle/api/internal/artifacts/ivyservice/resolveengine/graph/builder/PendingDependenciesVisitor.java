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

import org.gradle.api.artifacts.ModuleIdentifier;

public interface PendingDependenciesVisitor {

    enum PendingState {
        PENDING(true),
        NOT_PENDING(false),
        NOT_PENDING_ACTIVATING(false);

        private final boolean pending;

        PendingState(boolean pending) {
            this.pending = pending;
        }

        boolean isPending() {
            return this.pending;
        }
    }

    /**
     * If this dependency declaration is not a constraint, indicate whether an edge should be created.
     *
     * If this dependency declaration is a constraint:
     * <ul>
     *      <li>
     *          If the target module is <strong>already present</strong> in the graph: indicate that an edge should be created
     *      </li>
     *      <li>
     *          If the target module is <strong>not present</strong> in the graph: track the source node as a constraint provider for the target module
     *      </li>
     * </ul>
     *
     * @return The pending state of the <strong>target module</strong>
     */
    PendingState maybeAddAsPendingDependency(NodeState node, DependencyState dependencyState);

    boolean markNotPending(ModuleIdentifier id);

    void complete();
}
