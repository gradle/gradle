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

package org.gradle.internal.build

import org.gradle.execution.plan.Node
import org.gradle.execution.plan.PlannedNodeInternal
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.execution.plan.ToPlannedNodeConverter
import org.gradle.execution.plan.ToPlannedNodeConverterRegistry
import org.gradle.internal.taskgraph.NodeIdentity
import spock.lang.Specification

class PlannedNodeGraphTest extends Specification {

    // Anonymous classes to use as supported node types for converters (the count should be equal to the number of NodeIdentity.NodeType values)
    private static List<Class<? extends Node>> anonymousNodeTypes = [new TestNode("") {}, new TestNode("") {}]*.class

    def "can create collectors with different level of detail"() {
        def taskConverter = stubConverter(anonymousNodeTypes[0], NodeIdentity.NodeType.TASK)
        def transformStepConverter = stubConverter(anonymousNodeTypes[1], NodeIdentity.NodeType.TRANSFORM_STEP)

        when:
        def collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([taskConverter]))
        then:
        collector.detailLevel == PlannedNodeGraph.DetailLevel.LEVEL1_TASKS

        when:
        collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([taskConverter, transformStepConverter]))
        then:
        collector.detailLevel == PlannedNodeGraph.DetailLevel.LEVEL2_TRANSFORM_STEPS

        when:
        collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry(NodeIdentity.NodeType.values().toList().withIndex().collect { nodeType, index ->
            stubConverter(anonymousNodeTypes[index], nodeType)
        }))
        then:
        collector.detailLevel == PlannedNodeGraph.DetailLevel.values().max { it.level }

        when:
        new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([]))
        then:
        def e1 = thrown(IllegalStateException)
        e1.message == "Unknown detail level for node types: []"

        when:
        new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([transformStepConverter]))
        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Unknown detail level for node types: [TRANSFORM_STEP]"
    }

    def "plan node dependencies include transitively closest identifiable nodes"() {
        def taskConverter = new ToTestPlannedNodeConverter(TestTaskNode, NodeIdentity.NodeType.TASK)
        def collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([taskConverter]))

        def task1 = new TestTaskNode("task1")
        def task2 = new TestTaskNode("task2")
        def task3 = new TestTaskNode("task3")
        def task4 = new TestTaskNode("task4")
        def transform1 = new TestTransformStepNode("transform1")

        dependsOn(task1, [task2, transform1])
        dependsOn(task2, [task3])
        dependsOn(transform1, [task4])

        when:
        collector.collectNodes([task1, task2, task3, task4, transform1])
        def graph = collector.getGraph()
        def nodes = graph.getNodes(PlannedNodeGraph.DetailLevel.LEVEL1_TASKS) as List<TestPlannedNode>
        then:
        nodes.size() == 4
        nodes*.nodeIdentity*.nodeType =~ [NodeIdentity.NodeType.TASK]
        verifyAll {
            nodes[0].nodeIdentity.name == "task1"
            nodes[0].nodeDependencies*.name == ["task2", "task4"] // does not include task3, because it is "behind" task2, but includes task4, because it is "behind" non-identifiable transform1
            nodes[1].nodeIdentity.name == "task2"
            nodes[1].nodeDependencies*.name == ["task3"]
            nodes[2].nodeIdentity.name == "task3"
            nodes[2].nodeDependencies*.name == []
            nodes[3].nodeIdentity.name == "task4"
            nodes[3].nodeDependencies*.name == []
        }

        when:
        def nextLevelNodes = graph.getNodes(PlannedNodeGraph.DetailLevel.LEVEL2_TRANSFORM_STEPS)
        then:
        // Requesting higher-than-available detail level should return the same nodes
        nextLevelNodes == nodes
    }

    def dependsOn(TestNode node, List<TestNode> dependencies) {
        dependencies.forEach { node.addDependencySuccessor(it) }
    }

    def stubConverter(Class<? extends Node> supportedNodeType, NodeIdentity.NodeType nodeType) {
        Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> supportedNodeType
            getConvertedNodeType() >> nodeType
        }
    }

    static class ToTestPlannedNodeConverter implements ToPlannedNodeConverter {

        private final Class<? extends Node> supportedNodeType
        private final NodeIdentity.NodeType convertedNodeType

        ToTestPlannedNodeConverter(Class<? extends Node> supportedNodeType, NodeIdentity.NodeType convertedNodeType) {
            this.supportedNodeType = supportedNodeType
            this.convertedNodeType = convertedNodeType
        }

        @Override
        Class<? extends Node> getSupportedNodeType() {
            return supportedNodeType
        }

        @Override
        NodeIdentity.NodeType getConvertedNodeType() {
            return convertedNodeType
        }

        @Override
        TestNodeIdentity getNodeIdentity(Node node) {
            return new TestNodeIdentity(convertedNodeType, (node as TestNode).name)
        }

        @Override
        boolean isInSamePlan(Node node) {
            return true
        }

        @Override
        PlannedNodeInternal convert(Node node, List<? extends NodeIdentity> nodeDependencies) {
            return new TestPlannedNode(getNodeIdentity(node), nodeDependencies)
        }
    }

    static class TestNodeIdentity implements NodeIdentity {
        NodeType nodeType
        String name

        TestNodeIdentity(NodeType nodeType, String name) {
            this.nodeType = nodeType
            this.name = name
        }

        @Override
        NodeType getNodeType() {
            return nodeType
        }
    }

    static class TestPlannedNode implements PlannedNodeInternal {

        private final TestNodeIdentity nodeIdentity
        private final List<? extends NodeIdentity> dependencies

        TestPlannedNode(TestNodeIdentity nodeIdentity, List<? extends NodeIdentity> dependencies) {
            this.nodeIdentity = nodeIdentity
            this.dependencies = dependencies
        }

        @Override
        PlannedNodeInternal withNodeDependencies(List<? extends NodeIdentity> nodeDependencies) {
            return new TestPlannedNode(nodeIdentity, nodeDependencies)
        }

        @Override
        TestNodeIdentity getNodeIdentity() {
            return nodeIdentity
        }

        @Override
        List<? extends NodeIdentity> getNodeDependencies() {
            return dependencies
        }
    }

    static class TestNode extends Node {
        String name

        TestNode(String name) {
            this.name = name
        }

        @Override
        Throwable getNodeFailure() {
            return null
        }

        @Override
        void resolveDependencies(TaskDependencyResolver dependencyResolver) {}

        @Override
        String toString() {
            return "TestNode"
        }
    }

    static class TestTaskNode extends TestNode {
        TestTaskNode(String name) {
            super(name)
        }
    }

    static class TestTransformStepNode extends TestNode {
        TestTransformStepNode(String name) {
            super(name)
        }
    }
}
