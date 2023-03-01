/*
 * Copyright 2021 the original author or authors.
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A factory for creating and accessing ordinal nodes
 */
public class OrdinalNodeAccess {
    private final OrdinalGroupFactory ordinalGroups;
    private final Set<OrdinalNode> requiredNodes = new LinkedHashSet<>();

    public OrdinalNodeAccess(OrdinalGroupFactory ordinalGroups) {
        this.ordinalGroups = ordinalGroups;
    }

    void addDestroyerNode(OrdinalGroup ordinal, LocalTaskNode destroyer, Consumer<Node> ordinalNodeConsumer) {
        // Create (or get) a destroyer ordinal node that depends on the output locations of this task node
        ordinal.getDestroyerLocationsNode().addDependenciesFrom(destroyer);

        // Depend on any previous producer ordinal nodes (i.e. any producer ordinal nodes with a lower ordinal)
        if (ordinal.getPrevious() != null) {
            OrdinalNode producerLocations = ordinal.getPrevious().getProducerLocationsNode();
            maybeSchedule(ordinalNodeConsumer, producerLocations);
            destroyer.addDependencySuccessor(producerLocations);
        }
    }

    void addProducerNode(OrdinalGroup ordinal, LocalTaskNode producer, Consumer<Node> ordinalNodeConsumer) {
        // Create (or get) a producer ordinal node that depends on the dependencies of this task node
        ordinal.getProducerLocationsNode().addDependenciesFrom(producer);

        // Depend on any previous destroyer ordinal nodes (i.e. any destroyer ordinal nodes with a lower ordinal)
        if (ordinal.getPrevious() != null) {
            OrdinalNode destroyerLocations = ordinal.getPrevious().getDestroyerLocationsNode();
            maybeSchedule(ordinalNodeConsumer, destroyerLocations);
            producer.addDependencySuccessor(destroyerLocations);
        }
    }

    private void maybeSchedule(Consumer<Node> ordinalNodeConsumer, OrdinalNode node) {
        if (requiredNodes.add(node)) {
            node.require();
            for (Node successor : node.getDependencySuccessors()) {
                if (successor instanceof OrdinalNode) {
                    maybeSchedule(ordinalNodeConsumer, (OrdinalNode) successor);
                }
            }
            ordinalNodeConsumer.accept(node);
        }
    }

    List<OrdinalGroup> getAllGroups() {
        return ordinalGroups.getAllGroups();
    }

    Collection<? extends Node> getAllNodes() {
        return requiredNodes;
    }

    public OrdinalGroup group(int ordinal) {
        return ordinalGroups.group(ordinal);
    }

    public void reset() {
        requiredNodes.clear();
        ordinalGroups.reset();
    }
}
