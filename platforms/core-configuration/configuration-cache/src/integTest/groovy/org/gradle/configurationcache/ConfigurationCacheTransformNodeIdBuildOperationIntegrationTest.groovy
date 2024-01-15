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

package org.gradle.configurationcache

import com.google.common.collect.Iterables
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.resolve.transform.ArtifactTransformTestFixture
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.NodeIdentity

class ConfigurationCacheTransformNodeIdBuildOperationIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements ArtifactTransformTestFixture {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        // So that dependency resolution results from previous executions do not interfere
        requireOwnGradleUserHomeDir()
    }

    def "transform step node ids are stable when using load after store"() {
        createDirs("producer", "consumer")
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

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        configurationCacheRun ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")
        Map<String, Long> uniqueIdsFromStore = uniqueIdsPerColor

        when:
        configurationCacheRun ":consumer:resolve"
        then:
        uniqueIdsPerColor == uniqueIdsFromStore
    }

    private Map<String, Long> getUniqueIdsPerColor() {
        transformStepIdentities.collectEntries { color, identities ->
            def nodeIds = (identities*.transformStepNodeId as Set<Long>)
            [(color): Iterables.getOnlyElement(nodeIds)]
        }
    }

    private Map<String, List<Map<String, ?>>> getTransformStepIdentities() {
        def ops = buildOperations.all(CalculateTaskGraphBuildOperationType)
        def identities = ops.collect { op ->
            def plannedNodes = op.result.executionPlan as List<Map<String, ?>>
            def transformStepNodeIdentities = plannedNodes*.nodeIdentity.findAll { it.nodeType == NodeIdentity.NodeType.TRANSFORM_STEP.name() }
            return transformStepNodeIdentities
        }
        def targetColors = (identities.collect { it*.targetAttributes.color }.flatten() as Set<String>)
        return targetColors.collectEntries { color ->
            [(color): identities.collect {plannedTransformSteps ->
                Iterables.getOnlyElement(plannedTransformSteps.findAll { it.targetAttributes.color == color }) as Map<String, ?>
            }]
        }
    }
}
