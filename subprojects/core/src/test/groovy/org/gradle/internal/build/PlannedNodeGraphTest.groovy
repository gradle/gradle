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

import static org.gradle.internal.taskgraph.NodeIdentity.*

class PlannedNodeGraphTest extends Specification {

    // Anonymous classes to use as supported node types for converters (the count should be equal to the number of NodeIdentity.NodeType values)
    private static List<Class<? extends Node>> anonymousNodeTypes = [new TestNode("") {}, new TestNode("") {}]*.class

    def "can create collectors with different level of detail"() {
        def taskConverter = stubConverter(anonymousNodeTypes[0], NodeType.TASK)
        def transformStepConverter = stubConverter(anonymousNodeTypes[1], NodeType.TRANSFORM_STEP)

        when:
        def collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([taskConverter]))
        then:
        collector.detailLevel == PlannedNodeGraph.DetailLevel.LEVEL1_TASKS

        when:
        collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([taskConverter, transformStepConverter]))
        then:
        collector.detailLevel == PlannedNodeGraph.DetailLevel.LEVEL2_TRANSFORM_STEPS

        when:
        collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry(NodeType.values().toList().withIndex().collect { nodeType, index ->
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
        def taskConverter = new ToTestPlannedNodeConverter(TestTaskNode, NodeType.TASK)
        def collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([taskConverter]))

        def task1 = new TestTaskNode("task1")
        def task2 = new TestTaskNode("task2")
        def task3 = new TestTaskNode("task3")
        def task4 = new TestTaskNode("task4")
        def transform1 = new TestTransformStepNode("transform1")

        dependsOn(task1, [task2, transform1])
        dependsOn(task2, [task3])
        dependsOn(task3, [task4])
        dependsOn(transform1, [task4])

        when:
        collector.collectNodes([task1, task2, task3, task4, transform1])
        def graph = collector.getGraph()
        def nodes = graph.getNodes(PlannedNodeGraph.DetailLevel.LEVEL1_TASKS) as List<TestPlannedNode>
        then:
        nodes.size() == 4
        nodes*.nodeIdentity*.nodeType =~ [NodeType.TASK]
        verifyAll {
            nodes[0].nodeIdentity.name == "task1"
            nodes[0].nodeDependencies*.name == ["task2", "task4"] // does not include task3, because it is "behind" task2, but includes task4, because it is "behind" non-identifiable transform1
            nodes[1].nodeIdentity.name == "task2"
            nodes[1].nodeDependencies*.name == ["task3"]
            nodes[2].nodeIdentity.name == "task3"
            nodes[2].nodeDependencies*.name == ["task4"]
            nodes[3].nodeIdentity.name == "task4"
            nodes[3].nodeDependencies*.name == []
        }

        when:
        def nextLevelNodes = graph.getNodes(PlannedNodeGraph.DetailLevel.LEVEL2_TRANSFORM_STEPS)
        then:
        // Requesting higher-than-available detail level should return the same nodes
        nextLevelNodes == nodes
    }

    def "can obtain a plan with lower detail level"() {
        def taskConverter = new ToTestPlannedNodeConverter(TestTaskNode, NodeType.TASK)
        def transformStepConverter = new ToTestPlannedNodeConverter(TestTransformStepNode, NodeType.TRANSFORM_STEP)
        def collector = new PlannedNodeGraph.Collector(new ToPlannedNodeConverterRegistry([taskConverter, transformStepConverter]))

        def task1 = new TestTaskNode("task1")
        def task2 = new TestTaskNode("task2")
        def task3 = new TestTaskNode("task3")
        def task4 = new TestTaskNode("task4")
        def transformStep1 = new TestTransformStepNode("transformStep1")
        def transformStep2 = new TestTransformStepNode("transformStep2")
        def other1 = new TestNode("other1")
        def other2 = new TestNode("other2")

        dependsOn(task1, [other1])
        dependsOn(other1, [transformStep1])
        dependsOn(transformStep1, [task2, other2])
        dependsOn(other2, [task3])
        dependsOn(task4, [transformStep2])

        when:
        collector.collectNodes([task1, task2, task3, task4, transformStep1, transformStep2, other1, other2])
        def graph = collector.getGraph()
        def nodes = graph.getNodes(PlannedNodeGraph.DetailLevel.LEVEL2_TRANSFORM_STEPS) as List<TestPlannedNode>
        then:
        nodes.size() == 6
        nodes*.nodeIdentity*.nodeType =~ [NodeType.TASK, NodeType.TRANSFORM_STEP]
        verifyAll {
            nodes[0].nodeIdentity.name == "task1"
            nodes[0].nodeDependencies*.name == ["transformStep1"]
            nodes[1].nodeIdentity.name == "task2"
            nodes[1].nodeDependencies*.name == []
            nodes[2].nodeIdentity.name == "task3"
            nodes[2].nodeDependencies*.name == []
            nodes[3].nodeIdentity.name == "task4"
            nodes[3].nodeDependencies*.name == ["transformStep2"]
            nodes[4].nodeIdentity.name == "transformStep1"
            nodes[4].nodeDependencies*.name == ["task2", "task3"]
            nodes[5].nodeIdentity.name == "transformStep2"
            nodes[5].nodeDependencies*.name == []
        }

        when:
        nodes = graph.getNodes(PlannedNodeGraph.DetailLevel.LEVEL1_TASKS) as List<TestPlannedNode>
        then:
        nodes.size() == 4
        nodes*.nodeIdentity*.nodeType =~ [NodeType.TASK]
        verifyAll {
            nodes[0].nodeIdentity.name == "task1"
            nodes[0].nodeDependencies*.name == ["task2", "task3"]
            nodes[1].nodeIdentity.name == "task2"
            nodes[1].nodeDependencies*.name == []
            nodes[2].nodeIdentity.name == "task3"
            nodes[2].nodeDependencies*.name == []
            nodes[3].nodeIdentity.name == "task4"
            nodes[3].nodeDependencies*.name == []
        }
    }

    def dependsOn(TestNode node, List<TestNode> dependencies) {
        dependencies.forEach { node.addDependencySuccessor(it) }
    }

    def stubConverter(Class<? extends Node> supportedNodeType, NodeType nodeType) {
        Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> supportedNodeType
            getConvertedNodeType() >> nodeType
        }
    }

    static class ToTestPlannedNodeConverter implements ToPlannedNodeConverter {

        private final Class<? extends Node> supportedNodeType
        private final NodeType convertedNodeType

        ToTestPlannedNodeConverter(Class<? extends Node> supportedNodeType, NodeType convertedNodeType) {
            this.supportedNodeType = supportedNodeType
            this.convertedNodeType = convertedNodeType
        }

        @Override
        Class<? extends Node> getSupportedNodeType() {
            return supportedNodeType
        }

        @Override
        NodeType getConvertedNodeType() {
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

        @Override
        String toString() {
            return name
        }

        @Override
        boolean equals(o) {
            if (this.is(o)) {
                return true
            }
            if (!(o instanceof TestNodeIdentity)) {
                return false
            }

            TestNodeIdentity that = (TestNodeIdentity) o

            if (name != that.name) {
                return false
            }
            if (nodeType != that.nodeType) {
                return false
            }

            return true
        }

        @Override
        int hashCode() {
            int result
            result = nodeType.hashCode()
            result = 31 * result + name.hashCode()
            return result
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

        @Override
        String toString() {
            return "TestPlannedNode($nodeIdentity)"
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
            return "TestNode($name)"
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
