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

import com.google.common.collect.Maps;
import com.google.common.collect.Streams;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A factory for creating and accessing ordinal nodes
 */
public class OrdinalNodeAccess {
    private final Map<Integer, OrdinalGroup> groups = Maps.newHashMap();
    private final Map<OrdinalGroup, OrdinalNode> destroyerLocationNodes = Maps.newHashMap();
    private final Map<OrdinalGroup, OrdinalNode> producerLocationNodes = Maps.newHashMap();

    OrdinalNode getOrCreateDestroyableLocationNode(OrdinalGroup ordinal) {
        return destroyerLocationNodes.computeIfAbsent(ordinal, i -> createDestroyerLocationNode(ordinal));
    }

    OrdinalNode getOrCreateOutputLocationNode(OrdinalGroup ordinal) {
        return producerLocationNodes.computeIfAbsent(ordinal, i -> createProducerLocationNode(ordinal));
    }

    List<OrdinalNode> getAllNodes() {
        return Streams.concat(destroyerLocationNodes.values().stream(), producerLocationNodes.values().stream()).collect(Collectors.toList());
    }

    /**
     * Create relationships between the ordinal nodes such that destroyer ordinals cannot complete until all preceding producer
     * ordinals have completed (and vice versa).  This ensures that an ordinal does not complete early simply because the nodes in
     * the ordinal group it represents have no explicit dependencies.
     */
    void createInterNodeRelationships() {
        destroyerLocationNodes.forEach((ordinal, destroyer) -> {
            for (int i = 0; i < ordinal.getOrdinal(); i++) {
                Node precedingNode = destroyerLocationNodes.get(group(i));
                if (precedingNode != null) {
                    destroyer.addDependencySuccessor(precedingNode);
                }
            }
        });
        producerLocationNodes.forEach((ordinal, producer) -> {
            for (int i = 0; i < ordinal.getOrdinal(); i++) {
                Node precedingNode = producerLocationNodes.get(group(i));
                if (precedingNode != null) {
                    producer.addDependencySuccessor(precedingNode);
                }
            }
        });
    }

    private OrdinalNode createDestroyerLocationNode(OrdinalGroup ordinal) {
        return createOrdinalNode(OrdinalNode.Type.DESTROYER, ordinal);
    }

    private OrdinalNode createProducerLocationNode(OrdinalGroup ordinal) {
        return createOrdinalNode(OrdinalNode.Type.PRODUCER, ordinal);
    }

    private OrdinalNode createOrdinalNode(OrdinalNode.Type type, OrdinalGroup ordinal) {
        OrdinalNode ordinalNode = new OrdinalNode(type, ordinal);
        ordinalNode.require();
        return ordinalNode;
    }

    public OrdinalGroup group(int ordinal) {
        return groups.computeIfAbsent(ordinal, integer -> new OrdinalGroup(ordinal));
    }

    @Nullable
    public Node getPrecedingProducerLocationNode(OrdinalGroup ordinal) {
        if (ordinal.getOrdinal() == 0) {
            return null;
        } else {
            return getOrCreateOutputLocationNode(group(ordinal.getOrdinal() - 1));
        }
    }

    @Nullable
    public Node getPrecedingDestroyerLocationNode(OrdinalGroup ordinal) {
        if (ordinal.getOrdinal() == 0) {
            return null;
        } else {
            return getOrCreateDestroyableLocationNode(group(ordinal.getOrdinal() - 1));
        }
    }
}
