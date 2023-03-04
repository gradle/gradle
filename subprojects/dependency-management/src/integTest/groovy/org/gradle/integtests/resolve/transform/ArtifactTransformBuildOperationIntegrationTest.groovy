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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedNode
import org.gradle.internal.taskgraph.NodeIdentity
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType

import java.util.function.Predicate

class ArtifactTransformBuildOperationIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture {

    @EqualsAndHashCode
    static class TypedNodeId {
        String nodeType
        String nodeIdInType
    }

    static class NodeMatcher {
        // An ephemeral id provided by the test author only to match this id with other nodes via `dependencyNodeIds`
        String nodeId
        String nodeType
        Predicate<NodeIdentity> identityPredicate
        List<String> dependencyNodeIds = []

        def matchNode(plannedNode) {
            plannedNode.nodeIdentity.nodeType.toString() == nodeType && identityPredicate.test(plannedNode.nodeIdentity)
        }
    }

    static class PlannedTransformStepIdentityWithoutId {
        String consumerBuildPath
        String consumerProjectPath
        Map<String, String> componentId
        Map<String, String> sourceAttributes
        Map<String, String> targetAttributes
        List<Map<String, String>> capabilities
        String artifactName
        Map<String, String> dependenciesConfigurationIdentity
    }

    static final Set<String> KNOWN_NODE_TYPES = NodeIdentity.NodeType.values()*.name() as Set<String>
    static final String TASK = NodeIdentity.NodeType.TASK.name()
    static final String TRANSFORM_STEP = NodeIdentity.NodeType.TRANSFORM_STEP.name()

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        requireOwnGradleUserHomeDir()

        buildFile << """
            allprojects {
                group = "colored"
            }
        """
    }

    def setupExternalDependency() {
        def m1 = mavenRepo.module("test", "test", "4.2").publish()
        m1.artifactFile.text = "test-test"

        buildFile << """
            allprojects {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                dependencies {
                    implementation 'test:test:4.2'
                }
            }
        """
    }

    def "single transform operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformImplementation()
        setupExternalDependency()

        buildFile << """
            allprojects {
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

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        List<PlannedNode> plannedNodes = getPlannedNodes(1)

        def expectedTransformId = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId, ["node1"]),
                taskMatcher("node3", ":consumer:resolve", ["node2"]),
            ]
        )

        List<BuildOperationRecord> executeTransformationOps = getExecuteTransformOperations(1)

        with(executeTransformationOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "chained transform operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithChainedColorTransform()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeColor")
            .assertOutputContains("processing [producer.jar]")
            .assertOutputContains("processing [producer.jar.red]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.red.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "red", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                transformStepMatcher("node3", expectedTransformId2, ["node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)
        with(executeTransformationOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeColor"

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId2)
            transformActionClass == "MakeColor"

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "transform output used as another transform input operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformWithAnotherTransformOutputAsInput()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                    transform project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing producer.jar using [producer.jar.red, test-4.2.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                transformStepMatcher("node3", expectedTransformId2, ["node1", "node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)

        with(executeTransformationOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeRed"

            transformerName == "MakeRed"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId2)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "single transform with upstream dependencies operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformThatTakesUpstreamArtifacts()
        setupExternalDependency()

        buildFile << """
            allprojects {
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

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing producer.jar using [test-4.2.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(1)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: [buildPath: ":", projectPath: ":consumer", name: "resolver"],
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                taskMatcher("node3", ":consumer:resolve", ["node2"]),
            ]
        )

        with(buildOperations.only(ExecutePlannedTransformStepBuildOperationType).details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "chained transform with upstream dependencies operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeRed")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing producer.jar.red using [test-4.2.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.red.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "red", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: [buildPath: ":", projectPath: ":consumer", name: "resolver"],
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                transformStepMatcher("node3", expectedTransformId2, ["node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)
        with(executeTransformationOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeRed"

            transformerName == "MakeRed"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId2)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "chained transform producing multiple artifacts operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeColor) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'red')
                        parameters.targetColor.set('red')
                        parameters.multiplier.set(2)
                    }
                    registerTransform(MakeColor) {
                        from.attribute(color, 'red')
                        to.attribute(color, 'green')
                        parameters.targetColor.set('green')
                        parameters.multiplier.set(1)
                    }
                }
            }

            interface TargetColor extends TransformParameters {
                @Input
                Property<String> getTargetColor()
                @Input
                Property<Integer> getMultiplier()
            }

            abstract class MakeColor implements TransformAction<TargetColor> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert input.file
                    for (def i : 1..parameters.multiplier.get()) {
                        def output = outputs.file(input.name + "." + parameters.targetColor.get() + "-" + i)
                        output.text = input.text + "-" + parameters.targetColor.get() + "-" + i
                    }
                }
            }
        """
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeColor")
            .assertOutputContains("processing [producer.jar]")
            .assertOutputContains("processing [producer.jar.red-1]")
            .assertOutputContains("processing [producer.jar.red-2]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.red-1.green-1, producer.jar.red-2.green-1, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "red", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                transformStepMatcher("node3", expectedTransformId2, ["node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)
        with(executeTransformationOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeColor"

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId2)
            transformActionClass == "MakeColor"

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "single transform consuming multiple artifacts from task"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        taskTypeWithMultipleOutputFileProperties()
        setupBuildWithColorVariants()

        buildFile << """
            allprojects {
                task producer(type: OutputFilesTask) {
                    out1.convention(layout.buildDirectory.file("\${project.name}.out1.jar"))
                    out2.convention(layout.buildDirectory.file("\${project.name}.out2.jar"))
                }

                artifacts {
                    implementation producer.out1
                    implementation producer.out2
                }

                task resolve(type: ShowFileCollection) {
                    def view = configurations.resolver.incoming.artifactView {
                        attributes.attribute(color, 'green')
                    }.files
                    files.from(view)
                }
            }
        """

        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                    }
                }
            }

            abstract class MakeGreen implements TransformAction<org.gradle.api.artifacts.transform.TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        setupExternalDependency()

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen", "producer.out1.jar (project :producer)")
            .assertOutputContains("processing [producer.out1.jar]")
        result.groupedOutput.transform("MakeGreen", "producer.out2.jar (project :producer)")
            .assertOutputContains("processing [producer.out2.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.out1.jar.green, producer.out2.jar.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.out1.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.out2.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                transformStepMatcher("node3", expectedTransformId2, ["node1"]),
                taskMatcher("node4", ":consumer:resolve", ["node2", "node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)
        // Order of scheduling/execution is not guaranteed between the transforms
        checkExecuteTransformOperation(executeTransformationOps, expectedTransformId1, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "producer.out1.jar (project :producer)",
        ])
        checkExecuteTransformOperation(executeTransformationOps, expectedTransformId2, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "producer.out2.jar (project :producer)",
        ])
    }

    def "failing transform"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorAttributes()
        setupExternalDependency()

        buildFile << """
            allprojects {
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

            abstract class MakeGreen implements TransformAction<org.gradle.api.artifacts.transform.TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    throw new IllegalStateException("failed making green: \${input.name}")
                }
            }
        """

        when:
        runAndFail ":consumer:resolve"

        then:
        failureCauseContains("failed making green: producer.jar")

        result.groupedOutput.transform("MakeGreen", "producer.jar (project :producer)")
            .assertOutputContains("processing [producer.jar]")


        def plannedNodes = getPlannedNodes(1)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                taskMatcher("node3", ":consumer:resolve", ["node2"]),
            ]
        )

        def executeTransformationOp = getExecuteTransformOperations(1, true).first()
        executeTransformationOp.failure.startsWith("org.gradle.api.internal.artifacts.transform.TransformException: Execution failed for MakeGreen:")

        with(executeTransformationOp.details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    void checkExecutionPlanMatchingDependencies(List<PlannedNode> plannedNodes, List<NodeMatcher> nodeMatchers) {
        Map<TypedNodeId, List<String>> expectedDependencyNodeIdsByTypedNodeId = [:]
        Map<TypedNodeId, String> nodeIdByTypedNodeId = [:]

        for (def plannedNode : plannedNodes) {
            def matchers = nodeMatchers.findAll { it.matchNode(plannedNode) }
            assert matchers.size() == 1, "Expected exactly one matcher for node ${plannedNode.nodeIdentity}, but found ${matchers.size()}"
            def nodeMatcher = matchers[0]
            def nodeId = getTypedNodeId(plannedNode.nodeIdentity)
            nodeIdByTypedNodeId[nodeId] = nodeMatcher.nodeId
            expectedDependencyNodeIdsByTypedNodeId[nodeId] = nodeMatcher.dependencyNodeIds
        }

        for (def plannedNode : plannedNodes) {
            def typedNodeId = getTypedNodeId(plannedNode.nodeIdentity)
            def expectedDependencyNodeIds = expectedDependencyNodeIdsByTypedNodeId[typedNodeId]
            assert expectedDependencyNodeIds != null, "No expected dependencies for node $plannedNode"

            def actualDependencyNodeIds = plannedNode.nodeDependencies.collect { depIdentity ->
                def depTypedNodeId = getTypedNodeId(depIdentity)
                def depNodeId = nodeIdByTypedNodeId[depTypedNodeId]
                assert depNodeId != null, "No node id for node $depIdentity"
                depNodeId
            }

            assert actualDependencyNodeIds ==~ expectedDependencyNodeIds
        }
    }

    def taskMatcher(String nodeId, String taskIdentityPath, List<String> dependencyNodeIds) {
        new NodeMatcher(
            nodeId: nodeId,
            nodeType: TASK,
            identityPredicate: { joinPaths(it.buildPath, it.taskPath) == taskIdentityPath },
            dependencyNodeIds: dependencyNodeIds
        )
    }

    def transformStepMatcher(
        String nodeId,
        PlannedTransformStepIdentityWithoutId plannedTransformStepIdentity,
        List<String> dependencyNodeIds
    ) {
        new NodeMatcher(
            nodeId: nodeId,
            nodeType: TRANSFORM_STEP,
            identityPredicate: { matchTransformationIdentity(it, plannedTransformStepIdentity) },
            dependencyNodeIds: dependencyNodeIds
        )
    }

    def getTypedNodeId(nodeIdentity) {
        switch (nodeIdentity.nodeType) {
            case TASK:
                return new TypedNodeId(nodeType: TASK, nodeIdInType: nodeIdentity.taskId.toString())
            case TRANSFORM_STEP:
                return new TypedNodeId(nodeType: TRANSFORM_STEP, nodeIdInType: nodeIdentity.transformStepNodeId)
            default:
                throw new IllegalArgumentException("Unknown node type: ${nodeIdentity.nodeType}")
        }
    }

    def joinPaths(String... paths) {
        paths.inject("") { acc, path ->
            acc + (acc.endsWith(":") && path.startsWith(":") ? path.substring(1) : path)
        }
    }

    boolean matchTransformationIdentity(actual, PlannedTransformStepIdentityWithoutId expected) {
        actual.nodeType.toString() == TRANSFORM_STEP &&
            actual.consumerBuildPath == expected.consumerBuildPath &&
            actual.consumerProjectPath == expected.consumerProjectPath &&
            actual.componentId == expected.componentId &&
            actual.sourceAttributes == expected.sourceAttributes &&
            actual.targetAttributes == expected.targetAttributes &&
            actual.capabilities == expected.capabilities &&
            actual.artifactName == expected.artifactName &&
            actual.dependenciesConfigurationIdentity == expected.dependenciesConfigurationIdentity
    }

    void verifyTransformationIdentity(actual, PlannedTransformStepIdentityWithoutId expected) {
        verifyAll(actual) {
            nodeType.toString() == TRANSFORM_STEP
            consumerBuildPath == expected.consumerBuildPath
            consumerProjectPath == expected.consumerProjectPath
            componentId == expected.componentId
            sourceAttributes == expected.sourceAttributes
            targetAttributes == expected.targetAttributes
            capabilities == expected.capabilities
            artifactName == expected.artifactName
            dependenciesConfigurationIdentity == expected.dependenciesConfigurationIdentity
        }
    }

    def getPlannedNodes(int transformNodeCount) {
        def plannedNodes = buildOperations.only(CalculateTaskGraphBuildOperationType).result.executionPlan as List<PlannedNode>
        assert plannedNodes.every { KNOWN_NODE_TYPES.contains(it.nodeIdentity.nodeType) }
        assert plannedNodes.count { it.nodeIdentity.nodeType.toString() == TRANSFORM_STEP } == transformNodeCount
        return plannedNodes
    }

    def getExecuteTransformOperations(int expectOperationsCount, boolean expectFailure = false) {
        def operations = buildOperations.all(ExecutePlannedTransformStepBuildOperationType)
        assert operations.every { (it.failure != null) == expectFailure }
        assert operations.size() == expectOperationsCount
        return operations
    }

    void checkExecuteTransformOperation(List<BuildOperationRecord> executeOperations, PlannedTransformStepIdentityWithoutId expectedTransformId, Map<String, Object> expectedDetails) {
        def matchedOperations = executeOperations.findAll {
            matchTransformationIdentity(it.details.plannedTransformStepIdentity, expectedTransformId)
        }
        assert matchedOperations.size() == 1
        def operation = matchedOperations[0]

        verifyAll(operation.details) {
            transformActionClass == expectedDetails.transformActionClass

            transformerName == expectedDetails.transformerName
            subjectName == expectedDetails.subjectName
        }
    }
}
