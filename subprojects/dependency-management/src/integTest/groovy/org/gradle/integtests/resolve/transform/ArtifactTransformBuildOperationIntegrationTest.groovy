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

package org.gradle.integtests.resolve.transform

import groovy.transform.EqualsAndHashCode
import org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedNode
import org.gradle.internal.taskgraph.NodeIdentity

import java.util.function.Predicate

class ArtifactTransformBuildOperationIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture {

    @EqualsAndHashCode
    static class NodeId {
        String nodeType
        String nodeIdInType
    }

    static class NodeMatcher {
        String phantomId
        String nodeType
        Predicate<NodeIdentity> identityPredicate
        List<String> dependencyPhantomIds = []

        def matchNode(plannedNode) {
            plannedNode.nodeIdentity.nodeType.toString() == nodeType && identityPredicate.test(plannedNode.nodeIdentity)
        }
    }

    static final Set<String> KNOWN_NODE_TYPES = NodeIdentity.NodeType.values().collect { it.name() } as Set<String>
    static final String TASK = NodeIdentity.NodeType.TASK.name()
    static final String ARTIFACT_TRANSFORM = NodeIdentity.NodeType.ARTIFACT_TRANSFORM.name()

    // TODO:
    // - configuration cache tests: load-after-store
    // - up-to-date'ness sort of

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    def "single transform"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorAttributes()

        def m1 = mavenRepo.module("test", "test", "4.2").publish()
        m1.artifactFile.text = "test-test"

        buildFile << """
            allprojects {
                group = "colored"

                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    implementation 'test:test:4.2'
                }

                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                    }
                }
            }
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        file("buildSrc/src/main/groovy/MakeGreen.groovy") << """
            import org.gradle.api.file.*
            import org.gradle.api.provider.*
            import org.gradle.api.artifacts.transform.*

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()

                void transform(TransformOutputs outputs) {
                    def inputFile = input.get().asFile
                    println "Transforming " + inputFile.name
                    def outputFile = outputs.file(inputFile.name + ".green")
                    outputFile.text = "green"
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        def taskGraphs = buildOperations.all(CalculateTaskGraphBuildOperationType)
        taskGraphs*.details.buildPath == [":buildSrc", ":"]

        def taskGraph = taskGraphs.find { it.details.buildPath == ":" }
        taskGraph.failure == null

        def plannedNodes = taskGraph.result.executionPlan as List<PlannedNode>
        plannedNodes.every { KNOWN_NODE_TYPES.contains(it.nodeIdentity.nodeType) }
        plannedNodes.count { it.nodeIdentity.nodeType.toString() == ARTIFACT_TRANSFORM } == 1

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformMatcher("node2", ":consumer", [buildPath: ":", projectPath: ":producer"], [artifactType: "jar", color: "green"], ["node1"]),
                taskMatcher("node3", ":consumer:resolve", ["node2"]),
            ]
        )

        with(buildOperations.only(ExecuteScheduledTransformationStepBuildOperationType)) {
            details.transformerName == "MakeGreen"
            details.subjectName == "producer.jar (project :producer)"
            details.transformationIdentity.nodeType == "ARTIFACT_TRANSFORM"
            details.transformationIdentity.buildPath == ":"
            details.transformationIdentity.projectPath == ":consumer"
            details.transformationIdentity.targetVariant.componentId == [buildPath: ":", projectPath: ":producer"]
            details.transformationIdentity.targetVariant.attributes == [color: "green", artifactType: "jar"]
            details.transformationIdentity.targetVariant.capabilities == [[group: "colored", name: "producer", version: "unspecified"]]
            details.transformationIdentity.artifactName == "producer.jar"
            details.transformationIdentity.dependenciesConfigurationIdentity == null
            details.transformType == "MakeGreen"
            details.sourceAttributes == [color: "blue", artifactType: "jar"]
            details.fromAttributes == [color: "blue"]
            details.toAttributes == [color: "green"]
        }
    }

    def checkExecutionPlanMatchingDependencies(List<PlannedNode> plannedNodes, List<NodeMatcher> nodeMatchers) {
        Map<NodeId, List<String>> expectedDependencyPhantomIdsByNodeId = [:]
        Map<NodeId, String> phantomIdByNodeId = [:]

        for (def plannedNode : plannedNodes) {
            def matchers = nodeMatchers.findAll { it.matchNode(plannedNode) }
            assert matchers.size() == 1, "Expected exactly one matcher for node ${plannedNode.nodeIdentity}, but found ${matchers.size()}"
            def nodeMatcher = matchers[0]
            def nodeId = getNodeId(plannedNode.nodeIdentity)
            phantomIdByNodeId[nodeId] = nodeMatcher.phantomId
            expectedDependencyPhantomIdsByNodeId[nodeId] = nodeMatcher.dependencyPhantomIds
        }

        for (def plannedNode : plannedNodes) {
            def nodeId = getNodeId(plannedNode.nodeIdentity)
            def expectedDependencyPhantomIds = expectedDependencyPhantomIdsByNodeId[nodeId]
            assert expectedDependencyPhantomIds != null, "No expected dependencies for node $plannedNode"

            def actualDependencyPhantomIds = plannedNode.nodeDependencies.collect { depIdentity ->
                def depNodeId = getNodeId(depIdentity)
                def depPhantomId = phantomIdByNodeId[depNodeId]
                assert depPhantomId != null, "No phantom id for node $depIdentity"
                depPhantomId
            }

            assert actualDependencyPhantomIds.toSet() == expectedDependencyPhantomIds.toSet()
        }

        return true
    }

    def taskMatcher(String phantomId, String taskIdentityPath, List<String> dependencyPhantomIds) {
        new NodeMatcher(
            phantomId: phantomId,
            nodeType: TASK,
            identityPredicate: { joinPaths(it.buildPath, it.taskPath) == taskIdentityPath },
            dependencyPhantomIds: dependencyPhantomIds
        )
    }

    def transformMatcher(
        String phantomId,
        String consumerIdentityPath,
        Map<String, String> componentId,
        Map<String, String> attributes,
        List<String> dependencyPhantomIds
    ) {
        new NodeMatcher(
            phantomId: phantomId,
            nodeType: ARTIFACT_TRANSFORM,
            identityPredicate: {
                joinPaths(it.buildPath, it.projectPath) == consumerIdentityPath &&
                    it.targetVariant.componentId == componentId &&
                    it.targetVariant.attributes == attributes
            },
            dependencyPhantomIds: dependencyPhantomIds
        )
    }

    def getComponentId(componentId) {

    }

    def getNodeId(nodeIdentity) {
        switch (nodeIdentity.nodeType) {
            case TASK:
                return new NodeId(nodeType: TASK, nodeIdInType: nodeIdentity.taskId.toString())
            case ARTIFACT_TRANSFORM:
                return new NodeId(nodeType: ARTIFACT_TRANSFORM, nodeIdInType: nodeIdentity.transformationNodeId)
            default:
                throw new IllegalArgumentException("Unknown node type: ${nodeIdentity.nodeType}")
        }
    }

    def joinPaths(String... paths) {
        paths.inject("") { acc, path ->
            acc + (acc.endsWith(":") && path.startsWith(":") ? path.substring(1) : path)
        }
    }
}
