/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.build;

import com.google.common.collect.ImmutableList;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.PlannedNodeInternal;
import org.gradle.execution.plan.ToPlannedNodeConverter;
import org.gradle.execution.plan.ToPlannedNodeConverterRegistry;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedNode;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.TaskIdentity;
import org.gradle.internal.taskgraph.NodeIdentity;
import org.gradle.internal.taskgraph.NodeIdentity.NodeType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Collections.newSetFromMap;

/**
 * A graph of planned nodes that supports extracting sub-graphs of different {@link DetailLevel levels of detail}
 * depending on what node types are supported by the consumer.
 *
 * @see Collector
 */
public class PlannedNodeGraph {

    private final DetailLevel detailLevel;
    private final List<PlannedNodeInternal> plannedNodes;

    public PlannedNodeGraph(DetailLevel detailLevel, List<PlannedNodeInternal> plannedNodes) {
        this.detailLevel = detailLevel;
        this.plannedNodes = ImmutableList.copyOf(plannedNodes);
    }

    /**
     * Returns the nodes from a subgraph that are of the given or lower detail level
     * (lower if the requested detail level is higher than the available detail level).
     */
    public List<? extends PlannedNode> getNodes(DetailLevel detailLevel) {
        if (detailLevel.level >= this.detailLevel.level) {
            return plannedNodes;
        }

        return computePlan(detailLevel);
    }

    private List<PlannedNodeInternal> computePlan(DetailLevel detailLevel) {
        HashMap<NodeIdentity, List<? extends NodeIdentity>> plannedNodeByIdentity = new HashMap<>();
        for (PlannedNodeInternal plannedNode : plannedNodes) {
            plannedNodeByIdentity.put(plannedNode.getNodeIdentity(), plannedNode.getNodeDependencies());
        }

        Function<NodeIdentity, NodeIdentity> maybeIdentityProvider = id -> detailLevel.contains(id.getNodeType()) ? id : null;
        Function<NodeIdentity, List<? extends NodeIdentity>> traverser = id -> {
            List<? extends NodeIdentity> deps = plannedNodeByIdentity.get(id);
            if (deps == null) {
                throw new IllegalStateException("No dependencies for node: " + id);
            }
            return deps;
        };

        List<PlannedNodeInternal> newPlannedNodes = new ArrayList<>();
        for (PlannedNodeInternal plannedNode : plannedNodes) {
            NodeIdentity nodeIdentity = plannedNode.getNodeIdentity();
            if (!detailLevel.contains(nodeIdentity.getNodeType())) {
                continue;
            }

            List<? extends NodeIdentity> nodeDependencies = plannedNode.getNodeDependencies();
            if (nodeDependencies.isEmpty() || nodeDependencies.stream().allMatch(it -> detailLevel.contains(it.getNodeType()))) {
                newPlannedNodes.add(plannedNode);
            } else {
                List<NodeIdentity> newNodeDependencies = computeDependencies(traverser, maybeIdentityProvider, nodeIdentity);
                newPlannedNodes.add(plannedNode.withNodeDependencies(newNodeDependencies));
            }
        }

        return newPlannedNodes;
    }

    public enum DetailLevel {
        LEVEL1_TASKS(1, NodeType.TASK),
        LEVEL2_TRANSFORM_STEPS(2, NodeType.TASK, NodeType.TRANSFORM_STEP);

        private final int level;
        private final Set<NodeType> nodeTypes;

        DetailLevel(int level, NodeType... nodeTypes) {
            this.level = level;
            this.nodeTypes = EnumSet.copyOf(Arrays.asList(nodeTypes));
        }

        public int getLevel() {
            return level;
        }

        public boolean contains(NodeType nodeType) {
            return nodeTypes.contains(nodeType);
        }

        public static DetailLevel from(Set<NodeType> nodeTypes) {
            for (DetailLevel detailLevel : values()) {
                if (detailLevel.nodeTypes.equals(nodeTypes)) {
                    return detailLevel;
                }
            }

            throw new IllegalStateException("Unknown detail level for node types: " + nodeTypes);
        }
    }

    /**
     * {@link #collectNodes(Collection) Collects} and converts nodes to planned nodes
     * resolving their dependencies with the highest available {@link DetailLevel detail level}.
     */
    public static class Collector {

        private final ToPlannedNodeConverterRegistry converterRegistry;
        private final DetailLevel detailLevel;

        private final List<PlannedNodeInternal> plannedNodes = new ArrayList<>();
        // This cache is necessary, because PlannedNodeGraph relies on node identities being reference-equal
        private final Map<Node, NodeIdentity> nodeIdentityCache = new IdentityHashMap<>();

        public Collector(ToPlannedNodeConverterRegistry converterRegistry) {
            this.converterRegistry = converterRegistry;
            this.detailLevel = DetailLevel.from(converterRegistry.getConvertedNodeTypes());
        }

        public DetailLevel getDetailLevel() {
            return detailLevel;
        }

        public void collectNodes(Collection<Node> nodes) {
            for (Node node : nodes) {
                ToPlannedNodeConverter converter = converterRegistry.getConverter(node);
                if (converter != null && converter.isInSamePlan(node)) {
                    List<? extends NodeIdentity> nodeDependencies = findNodeDependencies(node);
                    PlannedNodeInternal plannedNode = converter.convert(node, nodeDependencies, () -> findTaskDependencies(node));
                    plannedNodes.add(plannedNode);
                }
            }
        }

        public PlannedNodeGraph getGraph() {
            return new PlannedNodeGraph(detailLevel, plannedNodes);
        }

        private List<? extends NodeIdentity> findNodeDependencies(Node node) {
            return findDependencies(node, Node::getDependencySuccessors, it -> true);
        }

        private List<TaskIdentity> findTaskDependencies(Node node) {
            @SuppressWarnings("unchecked")
            List<TaskIdentity> dependencies = (List<TaskIdentity>) findDependencies(node, Node::getDependencySuccessors, TaskIdentity.class::isInstance);
            return dependencies;
        }

        private List<? extends NodeIdentity> findDependencies(
            Node node,
            Function<? super Node, ? extends Collection<Node>> traverser,
            Predicate<NodeIdentity> identityFilter
        ) {
            return computeDependencies(traverser, n -> getNodeIdentityOrNull(n, identityFilter), node);
        }

        private NodeIdentity getNodeIdentityOrNull(Node node, Predicate<NodeIdentity> identityFilter) {
            ToPlannedNodeConverter converter = converterRegistry.getConverter(node);
            if (converter == null) {
                return null;
            }

            // Cache all identities even if the current predicate will filter them out afterwards
            NodeIdentity nodeIdentity = nodeIdentityCache.computeIfAbsent(node, converter::getNodeIdentity);
            return identityFilter.test(nodeIdentity) ? nodeIdentity : null;
        }
    }

    /**
     * Computes dependencies of a node in breadth-first order, stopping at each dependency for which identity is provided.
     */
    private static <T> List<NodeIdentity> computeDependencies(
        Function<? super T, ? extends Collection<? extends T>> traverser,
        Function<T, NodeIdentity> maybeIdentityProvider,
        T start
    ) {
        List<NodeIdentity> resultDependencies = new ArrayList<>();

        Queue<T> queue = new ArrayDeque<>(traverser.apply(start));
        Set<T> seen = newSetFromMap(new IdentityHashMap<>());

        while (!queue.isEmpty()) {
            T node = queue.remove();
            if (!seen.add(node)) {
                continue;
            }

            NodeIdentity identity = maybeIdentityProvider.apply(node);
            if (identity != null) {
                resultDependencies.add(identity);
                continue;
            }

            queue.addAll(traverser.apply(node));
        }

        return resultDependencies;
    }
}
