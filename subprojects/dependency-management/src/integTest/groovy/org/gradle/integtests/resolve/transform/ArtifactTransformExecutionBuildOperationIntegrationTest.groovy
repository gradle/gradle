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

import com.google.common.collect.Iterables
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.operations.dependencies.transforms.IdentifyTransformExecutionProgressDetails
import org.gradle.operations.dependencies.transforms.SnapshotTransformInputsBuildOperationType
import org.gradle.operations.execution.ExecuteWorkBuildOperationType
import org.gradle.test.fixtures.file.TestFile

class ArtifactTransformExecutionBuildOperationIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture, DirectoryBuildCacheFixture {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        requireOwnGradleUserHomeDir()

        // group name is included in the capabilities of components, which are part of the transform identity
        buildFile << """
            allprojects {
                group = "colored"
            }
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

    def "transform executions are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformImplementation()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")

                    artifactTypes {
                        jar {
                            attributes.attribute(color, 'blue')
                        }
                    }
                }
            }
        """

        when:
        withBuildCache().run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar.green]")

        def executionIdentifications = buildOperations.progress(IdentifyTransformExecutionProgressDetails)*.details
        executionIdentifications.size() == 2
        def projectTransformIdentification = executionIdentifications.find { it.inputArtifactName == 'producer.jar' }
        def externalTransformIdentification = executionIdentifications.find { it.inputArtifactName == 'test-4.2.jar' }

        List<BuildOperationRecord> executions = getTransformExecutions()
        executions.size() == 2
        def projectExecution = executions.find { it.details.workspaceId == projectTransformIdentification.uniqueId }
        def externalExecution = executions.find { it.details.workspaceId == externalTransformIdentification.uniqueId }

        with(projectExecution.result) {
            skipMessage == null
            failure == null
            originBuildInvocationId == null
            executionReasons == ['No history is available.']
        }
        with(Iterables.getOnlyElement(buildOperations.children(projectExecution, SnapshotTransformInputsBuildOperationType)).result) {
            hash != null
            classLoaderHash != null
            implementationClassName == 'MakeGreen'
            inputValueHashes.keySet() ==~ ['inputArtifactPath', 'inputPropertiesHash']
            outputPropertyNames == ['outputDirectory', 'resultsFile']
            actionClassNames == null
            actionClassLoaderHashes == null
            inputFileProperties.keySet() ==~ ['inputArtifact', 'inputArtifactDependencies']
            with(inputFileProperties.inputArtifactDependencies) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                roots == []
            }
            with(inputFileProperties.inputArtifact) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                with(Iterables.getOnlyElement(roots)) {
                    hash != null
                    path.endsWith('producer.jar')
                }
            }
        }
        with(Iterables.getOnlyElement(buildOperations.children(externalExecution, SnapshotTransformInputsBuildOperationType)).result) {
            hash != null
            classLoaderHash != null
            implementationClassName == 'MakeGreen'
            inputValueHashes.keySet() ==~ ['inputArtifactPath', 'inputArtifactSnapshot', 'inputPropertiesHash']
            outputPropertyNames == ['outputDirectory', 'resultsFile']
            actionClassNames == null
            actionClassLoaderHashes == null
            inputFileProperties.keySet() ==~ ['inputArtifact', 'inputArtifactDependencies']
            with(inputFileProperties.inputArtifactDependencies) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                roots == []
            }
            with(inputFileProperties.inputArtifact) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                with(Iterables.getOnlyElement(roots)) {
                    hash != null
                    path.endsWith('test-4.2.jar')
                }
            }
        }

    }

    List<BuildOperationRecord> getTransformExecutions() {
        buildOperations.all(ExecuteWorkBuildOperationType)
    }
}
