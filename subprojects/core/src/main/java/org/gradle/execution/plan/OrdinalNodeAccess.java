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

import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A factory for creating and accessing ordinal nodes
 */
public class OrdinalNodeAccess {
    TreeMap<Integer, Node> destroyerLocationNodes = Maps.newTreeMap();
    TreeMap<Integer, Node> producerLocationNodes = Maps.newTreeMap();

    Node getOrCreateDestroyableLocationNode(int ordinal) {
        return destroyerLocationNodes.computeIfAbsent(ordinal, this::createDestroyerLocationNode);
    }

    Node getOrCreateOutputLocationNode(int ordinal) {
        return producerLocationNodes.computeIfAbsent(ordinal, this::createProducerLocationNode);
    }

    Collection<Node> getPrecedingDestroyerLocationNodes(int from) {
        return destroyerLocationNodes.headMap(from).values();
    }

    Collection<Node> getPrecedingProducerLocationNodes(int from) {
        return producerLocationNodes.headMap(from).values();
    }

    List<Node> getAllNodes() {
        return Streams.concat(destroyerLocationNodes.values().stream(), producerLocationNodes.values().stream()).collect(Collectors.toList());
    }

    /**
     * Create relationships between the ordinal nodes such that destroyer ordinals cannot complete until all preceding producer
     * ordinals have completed (and vice versa).  This ensures that an ordinal does not complete early simply because the nodes in
     * the ordinal group it represents have no explicit dependencies.
     */
    void createInterNodeRelationships() {
        destroyerLocationNodes.forEach((ordinal, destroyer) -> getPrecedingProducerLocationNodes(ordinal).forEach(destroyer::addDependencySuccessor));
        producerLocationNodes.forEach((ordinal, producer) -> getPrecedingDestroyerLocationNodes(ordinal).forEach(producer::addDependencySuccessor));
    }

    private Node createDestroyerLocationNode(int ordinal) {
        return createOrdinalNode(OrdinalNode.Type.DESTROYER, ordinal);
    }

    private Node createProducerLocationNode(int ordinal) {
        return createOrdinalNode(OrdinalNode.Type.PRODUCER, ordinal);
    }

    private Node createOrdinalNode(OrdinalNode.Type type, int ordinal) {
        Node ordinalNode = new OrdinalNode(type, ordinal);
        ordinalNode.require();
        return ordinalNode;
    }
}
