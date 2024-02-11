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

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a set of nodes reachable from a particular entry point node (a "requested task")
 */
public class OrdinalGroup extends NodeGroup {
    private final int ordinal;
    @Nullable
    private final OrdinalGroup previous;
    private final Set<Node> entryNodes = new LinkedHashSet<>();
    private OrdinalNode producerLocationsNode;
    private OrdinalNode destroyerLocationsNode;

    OrdinalGroup(int ordinal, @Nullable OrdinalGroup previous) {
        this.ordinal = ordinal;
        this.previous = previous;
    }

    @Override
    public String toString() {
        return "task group " + ordinal;
    }

    @Nullable
    public OrdinalGroup getPrevious() {
        return previous;
    }

    @Nullable
    @Override
    public OrdinalGroup asOrdinal() {
        return this;
    }

    @Override
    public NodeGroup withOrdinalGroup(OrdinalGroup newOrdinal) {
        return newOrdinal;
    }

    @Override
    public NodeGroup reachableFrom(OrdinalGroup newOrdinal) {
        return newOrdinal;
    }

    public OrdinalNode getProducerLocationsNode() {
        if (producerLocationsNode == null) {
            producerLocationsNode = new OrdinalNode(OrdinalNode.Type.PRODUCER, this);
            if (previous != null) {
                producerLocationsNode.addDependencySuccessor(previous.getProducerLocationsNode());
            }
        }
        return producerLocationsNode;
    }

    public OrdinalNode getDestroyerLocationsNode() {
        if (destroyerLocationsNode == null) {
            destroyerLocationsNode = new OrdinalNode(OrdinalNode.Type.DESTROYER, this);
            if (previous != null) {
                destroyerLocationsNode.addDependencySuccessor(previous.getDestroyerLocationsNode());
            }
        }
        return destroyerLocationsNode;
    }

    @Override
    public boolean isReachableFromEntryPoint() {
        return true;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void addEntryNode(Node node) {
        entryNodes.add(node);
    }

    public String diagnostics() {
        return "group " + ordinal + " entry nodes: " + Node.formatNodes(entryNodes);
    }

    public OrdinalNode locationsNode(OrdinalNode.Type ordinalType) {
        if (ordinalType == OrdinalNode.Type.PRODUCER) {
            return getProducerLocationsNode();
        } else {
            return getDestroyerLocationsNode();
        }
    }
}
