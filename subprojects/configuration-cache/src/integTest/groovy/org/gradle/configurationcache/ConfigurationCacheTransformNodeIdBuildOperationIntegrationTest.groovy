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
        setupBuildWithColorTransformImplementation()
        settingsFile << """
            include 'producer', 'consumer'
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
        def nodeIdentities = transformStepIdentities
        def nodeIds = (nodeIdentities*.transformStepNodeId as Set<Long>)
        def uniqueId = Iterables.getOnlyElement(nodeIds)

        when:
        configurationCacheRun ":consumer:resolve"
        then:
        def nodeIdentity = Iterables.getOnlyElement(transformStepIdentities)
        nodeIdentity.transformStepNodeId == uniqueId
    }

    private List<Map<String, ?>> getTransformStepIdentities() {
        def ops = buildOperations.all(CalculateTaskGraphBuildOperationType)
        return ops.collect { op ->
            def plannedNodes = op.result.executionPlan as List<Map<String, ?>>
            def transformStepNodeIdentities = plannedNodes*.nodeIdentity.findAll { it.nodeType.toString() == NodeIdentity.NodeType.TRANSFORM_STEP.name() }
            assert transformStepNodeIdentities.size() == 1
            return transformStepNodeIdentities.first() as Map<String, ?>
        }
    }
}
