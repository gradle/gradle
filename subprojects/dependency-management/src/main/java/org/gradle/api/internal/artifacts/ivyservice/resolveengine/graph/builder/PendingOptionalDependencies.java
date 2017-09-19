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

import java.util.Set;

public class PendingOptionalDependencies {
    static final PendingOptionalDependencies NOT_OPTIONAL = new PendingOptionalDependencies(null);

    private boolean noLongerOptional;
    private final Set<NodeState> affectedComponents;

    public PendingOptionalDependencies() {
        this(Sets.<NodeState>newLinkedHashSet());
    }

    public PendingOptionalDependencies(Set<NodeState> nodeStates) {
        this.affectedComponents = nodeStates;
        if (nodeStates == null) {
            noLongerOptional = true;
        }
    }

    void addNode(NodeState state) {
        if (noLongerOptional) {
            throw new IllegalStateException("Cannot add a pending node for a dependency which is not optional");
        }
        affectedComponents.add(state);
    }

    void turnIntoHardDependencies() {
        noLongerOptional = true;
        for (NodeState affectedComponent : affectedComponents) {
            affectedComponent.resetSelectionState();
        }
    }

    public boolean isPending() {
        return !noLongerOptional;
    }
}
