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
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedNode
import org.gradle.internal.taskgraph.NodeIdentity
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType
import org.gradle.operations.dependencies.transforms.ExecuteTransformActionBuildOperationType
import org.gradle.operations.dependencies.transforms.IdentifyTransformExecutionProgressDetails
import org.gradle.operations.dependencies.transforms.SnapshotTransformInputsBuildOperationType
import org.gradle.operations.execution.ExecuteWorkBuildOperationType
import org.gradle.test.fixtures.file.TestFile

import java.util.function.Predicate

class ArtifactTransformBuildOperationIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture, DirectoryBuildCacheFixture {

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

        @Override
        String toString() {
            "NodeMatcher(nodeId=$nodeId, nodeType=$nodeType)"
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

        // group name is included in the capabilities of components, which are part of the transform identity
        buildFile << """
            allprojects {
                group = "colored"
            }
        """

        printTaskOnlyExecutionPlan()
    }

    def printTaskOnlyExecutionPlan(TestFile buildFile = getBuildFile()) {
        // Log a task-only execution plan, which can only be computed during the runtime of the build
        buildFile << """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.internal.operations.*
            import org.gradle.internal.taskgraph.*

            abstract class LoggingListener implements BuildOperationListener, BuildService<BuildServiceParameters.None> {
                void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) { throw new RuntimeException() }
                void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) { throw new RuntimeException() }
                void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
                    if (finishEvent.result instanceof CalculateTaskGraphBuildOperationType.Result) {
                        def plannedTasks = finishEvent.result.getExecutionPlan([NodeIdentity.NodeType.TASK] as Set)
                        println("Task-only execution plan: " + plannedTasks.collect { "PlannedTask('\${it.nodeIdentity}', deps=\${it.nodeDependencies})" })
                    }
                }
            }

            def listener = gradle.sharedServices.registerIfAbsent("listener", LoggingListener) { }
            services.get(BuildEventsListenerRegistry).onOperationCompletion(listener)
        """
    }

    def setupExternalDependency(TestFile buildFile = getBuildFile()) {
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        List<BuildOperationRecord> executePlannedStepOps = getExecutePlannedStepOperations(1)

        with(executePlannedStepOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }

        checkExecuteTransformWorkOperations(executePlannedStepOps[0], 1)
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        def executePlannedStepOps = getExecutePlannedStepOperations(2)
        with(executePlannedStepOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeColor"

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }

        with(executePlannedStepOps[1].details) {
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        def executePlannedStepOps = getExecutePlannedStepOperations(2)

        with(executePlannedStepOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeRed"

            transformerName == "MakeRed"
            subjectName == "producer.jar (project :producer)"
        }

        with(executePlannedStepOps[1].details) {
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        def executePlannedStepOps = getExecutePlannedStepOperations(2)
        with(executePlannedStepOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeRed"

            transformerName == "MakeRed"
            subjectName == "producer.jar (project :producer)"
        }

        with(executePlannedStepOps[1].details) {
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        def executePlannedStepOps = getExecutePlannedStepOperations(2)
        with(executePlannedStepOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeColor"

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }

        checkExecuteTransformWorkOperations(executePlannedStepOps[0], 1)

        with(executePlannedStepOps[1].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId2)
            transformActionClass == "MakeColor"

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }

        checkExecuteTransformWorkOperations(executePlannedStepOps[1], 2)
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        def executePlannedStepOps = getExecutePlannedStepOperations(2)
        // Order of scheduling/execution is not guaranteed between the transforms
        checkExecutePlannedStepOperation(executePlannedStepOps, expectedTransformId1, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "producer.out1.jar (project :producer)",
        ])
        checkExecutePlannedStepOperation(executePlannedStepOps, expectedTransformId2, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "producer.out2.jar (project :producer)",
        ])

        executePlannedStepOps.each {
            checkExecuteTransformWorkOperations(it, 1)
        }
    }

    def "single transform used by multiple consumers creates a node per consumer"() {
        settingsFile << """
            include 'producer', 'consumer1', 'consumer2'
        """

        setupBuildWithColorTransformImplementation()
        setupExternalDependency()

        buildFile << """
            project(":consumer1") {
                dependencies {
                    implementation project(":producer")
                }
            }
            project(":consumer2") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer1:resolve", ":consumer2:resolve"

        then:
        executedAndNotSkipped(":consumer1:resolve", ":consumer2:resolve")

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer1:resolve', deps=[Task :producer:producer]), PlannedTask('Task :consumer2:resolve', deps=[Task :producer:producer])]")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing [producer.jar]")
        result.groupedOutput.task(":consumer1:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")
        result.groupedOutput.task(":consumer2:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        List<PlannedNode> plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer1",
            componentId: [buildPath: ":", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer2",
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
                transformStepMatcher("node3", expectedTransformId2, ["node1"]),
                taskMatcher("node4", ":consumer1:resolve", ["node2"]),
                taskMatcher("node5", ":consumer2:resolve", ["node3"]),
            ]
        )

        List<BuildOperationRecord> executePlannedStepOps = getExecutePlannedStepOperations(2)
        // Order of scheduling/execution is not guaranteed between the consumer projects
        checkExecutePlannedStepOperation(executePlannedStepOps, expectedTransformId1, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "producer.jar (project :producer)",
        ])
        checkExecutePlannedStepOperation(executePlannedStepOps, expectedTransformId2, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "producer.jar (project :producer)",
        ])

        def executeWorkOps1 = buildOperations.children(executePlannedStepOps[0], ExecuteWorkBuildOperationType)
        def executeWorkOps2 = buildOperations.children(executePlannedStepOps[1], ExecuteWorkBuildOperationType)

        // Order of scheduling/execution is not guaranteed between the consumer projects
        checkExecuteTransformWorkOperations(executeWorkOps1 + executeWorkOps2, 1)
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer])]")

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

        def executePlannedStepOp = getExecutePlannedStepOperations(1, true).first()
        executePlannedStepOp.failure.startsWith("org.gradle.api.internal.artifacts.transform.TransformException: Execution failed for MakeGreen:")

        with(executePlannedStepOp.details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId1)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }

        checkExecuteTransformWorkOperations(executePlannedStepOp, 1)
    }

    def "planned transform for external dependency substituted by included build"() {
        file("included/settings.gradle") << """
            include 'nested-producer'
        """
        setupBuildWithColorAttributes(file("included/build.gradle"))

        settingsFile << """
            includeBuild("included") {
                dependencySubstitution {
                    substitute(module("test:test")).using(project(":nested-producer"))
                }
            }
        """

        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformImplementation()
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

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolve', deps=[Task :producer:producer, Task :included:nested-producer:producer])]")
        outputContains("Task-only execution plan: [PlannedTask('Task :included:nested-producer:producer', deps=[])]")

        result.groupedOutput.transform("MakeGreen", "producer.jar (project :producer)")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.transform("MakeGreen", "nested-producer.jar (project :included:nested-producer)")
            .assertOutputContains("processing [nested-producer.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, nested-producer.jar.green]")

        // Included build runs a single task and no transforms
        def includedPlannedNodes = getPlannedNodes(0, ":included")
        includedPlannedNodes.size() == 1

        def plannedNodes = getPlannedNodes(2, ":")

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

        def expectedTransformId2 = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":included", projectPath: ":nested-producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "included", name: "nested-producer", version: "unspecified"]],
            artifactName: "nested-producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            [*includedPlannedNodes, *plannedNodes],
            [
                taskMatcher("node1", ":producer:producer", []),
                transformStepMatcher("node2", expectedTransformId1, ["node1"]),
                taskMatcher("node3", ":included:nested-producer:producer", []),
                transformStepMatcher("node4", expectedTransformId2, ["node3"]),
                taskMatcher("node5", ":consumer:resolve", ["node2", "node4"]),
            ]
        )

        List<BuildOperationRecord> executePlannedStepOps = getExecutePlannedStepOperations(2)

        // Order of scheduling/execution is not guaranteed between the transforms
        checkExecutePlannedStepOperation(executePlannedStepOps, expectedTransformId1, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "producer.jar (project :producer)",
        ])
        checkExecutePlannedStepOperation(executePlannedStepOps, expectedTransformId2, [
            transformActionClass: "MakeGreen",
            transformerName: "MakeGreen",
            subjectName: "nested-producer.jar (project :included:nested-producer)",
        ])
    }

    def "included build transform operations are captured"() {
        file("included/settings.gradle") << """
            include 'producer', 'consumer'
        """
        def includedBuildFile = file("included/build.gradle")
        includedBuildFile << """
            allprojects {
                group = "colored"
            }
        """
        setupBuildWithColorTransformImplementation(includedBuildFile)
        setupExternalDependency(includedBuildFile)
        includedBuildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        settingsFile << """
            includeBuild("included")
        """

        buildFile << """
            task rootConsumer() {
                dependsOn(gradle.includedBuild("included").task(":consumer:resolve"))
            }
        """

        when:
        run ":rootConsumer"

        then:
        executedAndNotSkipped(":rootConsumer")

        outputContains("Task-only execution plan: [PlannedTask('Task :rootConsumer', deps=[Task :included:consumer:resolve])]")
        outputContains("Task-only execution plan: [PlannedTask('Task :included:producer:producer', deps=[]), PlannedTask('Task :included:consumer:resolve', deps=[Task :included:producer:producer])]")

        result.groupedOutput.transform("MakeGreen", "producer.jar (project :included:producer)")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.task(":included:consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        // Root build runs a single task and no transforms
        getPlannedNodes(0, ":").size() == 1

        def plannedNodes = getPlannedNodes(1, ":included")

        def expectedTransformId = new PlannedTransformStepIdentityWithoutId([
            consumerBuildPath: ":included",
            consumerProjectPath: ":consumer",
            componentId: [buildPath: ":included", projectPath: ":producer"],
            sourceAttributes: [color: "blue", artifactType: "jar"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":included:producer:producer", []),
                transformStepMatcher("node2", expectedTransformId, ["node1"]),
                taskMatcher("node3", ":included:consumer:resolve", ["node2"]),
            ]
        )

        List<BuildOperationRecord> executePlannedStepOps = getExecutePlannedStepOperations(1)

        with(executePlannedStepOps[0].details) {
            verifyTransformationIdentity(plannedTransformStepIdentity, expectedTransformId)
            transformActionClass == "MakeGreen"

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :included:producer)"
        }
    }

    def "project transform execution can be up-to-date or from build cache"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransform(buildFile)
        buildFile << """
            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                @PathSensitive(PathSensitivity.RELATIVE)
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
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
        withBuildCache().run ":consumer:resolve", "-DproducerContent=initial"
        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing [producer.jar]")

        checkExecuteTransformWorkOperations(getExecutePlannedStepOperations(1).first(), [null])

        when:
        withBuildCache().run ":consumer:resolve", "-DproducerContent=initial"
        then:
        executedAndNotSkipped(":consumer:resolve")

        outputDoesNotContain("processing [producer.jar]")
        checkExecuteTransformWorkOperations(getExecutePlannedStepOperations(1).first(), ["UP-TO-DATE"])

        when:
        withBuildCache().run ":consumer:resolve", "-DproducerContent=changed"
        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing [producer.jar]")

        checkExecuteTransformWorkOperations(getExecutePlannedStepOperations(1).first(), [null])

        when:
        withBuildCache().run ":consumer:resolve", "-DproducerContent=initial"
        then:
        executedAndNotSkipped(":consumer:resolve")

        outputDoesNotContain("processing [producer.jar]")
        checkExecuteTransformWorkOperations(getExecutePlannedStepOperations(1).first(), ["FROM-CACHE"])
    }

    def "build operation for planned steps executed non-planned"() {
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


            class ShowFileCollectionWithoutDependencies extends DefaultTask {
                @Internal
                final ConfigurableFileCollection files = project.objects.fileCollection()

                ShowFileCollectionWithoutDependencies() {
                    outputs.upToDateWhen { false }
                }

                @TaskAction
                def go() {
                    println "result = \${files.files.name}"
                }
            }

            project(":consumer") {
                tasks.register("resolveWithoutDependencies", ShowFileCollectionWithoutDependencies) {
                    def view = configurations.resolver.incoming.artifactView {
                        attributes.attribute(color, 'green')
                    }.files
                    files.from(view)
                    dependsOn(":producer:producer")
                }
            }
        """

        when:
        run ":consumer:resolveWithoutDependencies"

        then:
        executedAndNotSkipped(":consumer:resolveWithoutDependencies")

        outputContains("Task-only execution plan: [PlannedTask('Task :producer:producer', deps=[]), PlannedTask('Task :consumer:resolveWithoutDependencies', deps=[Task :producer:producer])]")

        result.groupedOutput.transform("MakeColor")
            .assertOutputContains("processing [producer.jar]")
            .assertOutputContains("processing [producer.jar.red]")

        result.groupedOutput.task(":consumer:resolveWithoutDependencies")
            .assertOutputContains("result = [producer.jar.red.green, test-4.2.jar]")

        getPlannedNodes(0)
        getExecutePlannedStepOperations(2).each {
            checkExecuteTransformWorkOperations(it, 1)
        }
    }

    def "planned transform steps from script plugin buildscript block are ignored"() {
        setupProjectTransformInBuildScriptBlock(true)

        when:
        run "consumer:hello"
        then:
        executedAndNotSkipped(":consumer:hello")

        outputContains("processing [nested-producer.jar]")
        outputContains("processing [nested-producer.jar.red]")
        outputContains("Task-only execution plan: [PlannedTask('Task :consumer:hello', deps=[])]")

        getPlannedNodes(0)
        getExecutePlannedStepOperations(0).empty

        buildOperations.progress(IdentifyTransformExecutionProgressDetails).size() == 3
        // The execution engine in DependencyManagementBuildScopeServices doesn't fire build operations,
        // except for instrumentation transforms
        buildOperations.all(ExecuteWorkBuildOperationType).size() == 1
        buildOperations.all(SnapshotTransformInputsBuildOperationType).size() == 1
        buildOperations.all(ExecuteTransformActionBuildOperationType).size() == 3
    }

    def "planned transform steps from project buildscript context are captured"() {
        setupProjectTransformInBuildScriptBlock(false)

        when:
        run "consumer:hello"
        then:
        executedAndNotSkipped(":consumer:hello")

        result.groupedOutput.transform("MakeColor")
            .assertOutputContains("processing [nested-producer.jar]")
            .assertOutputContains("processing [nested-producer.jar.red]")


        outputContains("Task-only execution plan: [PlannedTask('Task :consumer:hello', deps=[])]")

        getPlannedNodes(0)
        getExecutePlannedStepOperations(2)

        buildOperations.progress(IdentifyTransformExecutionProgressDetails).size() == 3
        // The execution engine in DependencyManagementBuildScopeServices doesn't fire build operations
        // except for instrumentation transforms
        buildOperations.all(ExecuteWorkBuildOperationType).size() == 1
        buildOperations.all(SnapshotTransformInputsBuildOperationType).size() == 1
        buildOperations.all(ExecuteTransformActionBuildOperationType).size() == 3
    }

    private void setupProjectTransformInBuildScriptBlock(boolean inExternalScript) {
        setupBuildWithChainedColorTransform()

        file("included/settings.gradle") << """
            include 'nested-producer'
        """
        setupBuildWithColorAttributes(file("included/build.gradle"))

        settingsFile << """
            includeBuild("included") {
                dependencySubstitution {
                    substitute(module("test:test")).using(project(":nested-producer"))
                }
            }
        """

        settingsFile << """
            include 'consumer'
        """

        // Can't define classes in buildscript block, so let's do it in buildSrc
        file("buildSrc/src/main/groovy/TargetColor.groovy") << """
            import ${TransformParameters.name}
            import ${Property.name}
            import ${Input.name}

            interface TargetColor extends TransformParameters {
                @Input
                Property<String> getTargetColor()
            }
        """

        file("buildSrc/src/main/groovy/MakeColor.groovy") << """
            import ${TransformAction.name}
            import ${TransformOutputs.name}
            import ${Provider.name}
            import ${FileSystemLocation.name}
            import ${InputArtifact.name}

            abstract class MakeColor implements TransformAction<TargetColor> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    def output = outputs.file(input.name + "." + parameters.targetColor.get())
                    if (input.file) {
                        output.text = input.text + "-" + parameters.targetColor.get()
                    } else {
                        output.text = "missing-" + parameters.targetColor.get()
                    }
                }
            }
        """

        def consumerBuildFile = file('consumer/build.gradle')
        def buildscriptDestination = inExternalScript
            ? file('consumer/my-script.gradle')
            : consumerBuildFile
        buildscriptDestination << """
            buildscript {
                // Can't use color, since we need to use the configuration directly and
                // can't go via an artifact view
                def artifactType = Attribute.of('artifactType', String)
                dependencies {
                    classpath("test:test:1.0")

                    registerTransform(MakeColor) {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'red')
                        parameters.targetColor.set('red')
                    }
                    registerTransform(MakeColor) {
                        from.attribute(artifactType, 'red')
                        to.attribute(artifactType, 'green')
                        parameters.targetColor.set('green')
                    }
                }
                configurations.classpath.attributes.attribute(artifactType, 'green')
            }
        """
        if (inExternalScript) {
            consumerBuildFile << """
                apply from: 'my-script.gradle'
            """
        }
        consumerBuildFile << """
            task hello {
                doLast {
                    println "Hello"
                }
            }
        """
    }

    void checkExecutionPlanMatchingDependencies(List<PlannedNode> plannedNodes, List<NodeMatcher> nodeMatchers) {
        Map<TypedNodeId, List<String>> expectedDependencyNodeIdsByTypedNodeId = [:]
        Map<TypedNodeId, String> nodeIdByTypedNodeId = [:]

        def usedMatchers = new HashSet<NodeMatcher>()
        for (def plannedNode : plannedNodes) {
            def matchers = nodeMatchers.findAll { it.matchNode(plannedNode) }
            assert matchers.size() == 1, "Expected exactly one matcher for node ${plannedNode.nodeIdentity}, but found ${matchers.size()}"
            def nodeMatcher = matchers[0]
            def nodeId = getTypedNodeId(plannedNode.nodeIdentity)
            assert !usedMatchers.contains(nodeMatcher)
            usedMatchers.add(nodeMatcher)
            nodeIdByTypedNodeId[nodeId] = nodeMatcher.nodeId
            expectedDependencyNodeIdsByTypedNodeId[nodeId] = nodeMatcher.dependencyNodeIds
        }
        def unusedMatchers = nodeMatchers.toSet().tap { it.removeAll(usedMatchers) }
        assert unusedMatchers.size() == 0

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

    List<PlannedNode> getPlannedNodes(int transformNodeCount, String buildPath = ":") {
        def ops = buildOperations.all(CalculateTaskGraphBuildOperationType)
            .findAll { it.details.buildPath == buildPath }
        assert !ops.empty
        if (GradleContextualExecuter.configCache) {
            assert ops.size() == 2
        } else {
            assert ops.size() == 1
        }
        return ops.collect { op ->
            def plannedNodes = op.result.executionPlan as List<PlannedNode>
            assert plannedNodes.every { KNOWN_NODE_TYPES.contains(it.nodeIdentity.nodeType) }
            assert plannedNodes.count { it.nodeIdentity.nodeType.toString() == TRANSFORM_STEP } == transformNodeCount
            return plannedNodes
        }.first()
    }

    def getExecutePlannedStepOperations(int expectOperationsCount, boolean expectFailure = false) {
        def operations = buildOperations.all(ExecutePlannedTransformStepBuildOperationType)
        assert operations.every { (it.failure != null) == expectFailure }
        assert operations.size() == expectOperationsCount
        return operations
    }

    void checkExecutePlannedStepOperation(List<BuildOperationRecord> executeOperations, PlannedTransformStepIdentityWithoutId expectedTransformId, Map<String, Object> expectedDetails) {
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

    void checkExecuteTransformWorkOperations(BuildOperationRecord parent, int expectOperationsCount) {
        def executeWorkOps = buildOperations.children(parent, ExecuteWorkBuildOperationType)
        checkExecuteTransformWorkOperations(executeWorkOps, expectOperationsCount)
    }

    void checkExecuteTransformWorkOperations(BuildOperationRecord parent, List<String> skipMessages) {
        def executeWorkOps = buildOperations.children(parent, ExecuteWorkBuildOperationType)
        checkExecuteTransformWorkOperations(executeWorkOps, skipMessages)
    }

    void checkExecuteTransformWorkOperations(List<BuildOperationRecord> executeWorkOps, int expectOperationsCount) {
        checkExecuteTransformWorkOperations(executeWorkOps, [null as String] * expectOperationsCount)
    }

    void checkExecuteTransformWorkOperations(List<BuildOperationRecord> executeWorkOps, List<String> skipMessages) {
        assert executeWorkOps.size() == skipMessages.size()
        [executeWorkOps, skipMessages].transpose().every { op, skipMessage ->
            verifyAll(op) {
                details.workType == "TRANSFORM"
                result.skipMessage == skipMessage
            }
        }
    }
}
