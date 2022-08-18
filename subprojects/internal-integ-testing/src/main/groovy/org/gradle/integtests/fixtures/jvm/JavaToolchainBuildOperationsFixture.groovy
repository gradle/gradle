/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm

import groovy.transform.SelfType
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.internal.tasks.execution.ResolveTaskMutationsBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.jvm.toolchain.internal.operations.JavaToolchainUsageProgressDetails
import org.junit.Before

@SelfType(AbstractIntegrationSpec)
trait JavaToolchainBuildOperationsFixture {

    private BuildOperationsFixture operations

    @Before
    void setupBuildOperations() {
        operations = new BuildOperationsFixture(executer, temporaryFolder)
    }

    // Spock 2 executes @Before after the setup() methods
    // this is a workaround for tests that use this fixture from their setup() methods
    private void initIfNeeded() {
        if (operations == null) {
            setupBuildOperations()
        }
    }

    static void assertToolchainUsages(List<BuildOperationRecord.Progress> events, JvmInstallationMetadata jdkMetadata, String tool) {
        assert events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage(tool, jdkMetadata, usageEvent)
        }
    }

    static void assertToolchainUsage(String toolName, JvmInstallationMetadata jdkMetadata, BuildOperationRecord.Progress usageEvent) {
        assert usageEvent.details.toolName == toolName

        def usedToolchain = usageEvent.details.toolchain
        assert usedToolchain == [
            javaVersion: jdkMetadata.javaVersion,
            javaVendor: jdkMetadata.vendor.displayName,
            runtimeName: jdkMetadata.runtimeName,
            runtimeVersion: jdkMetadata.runtimeVersion,
            jvmName: jdkMetadata.jvmName,
            jvmVersion: jdkMetadata.jvmVersion,
            jvmVendor: jdkMetadata.jvmVendor,
            architecture: jdkMetadata.architecture,
        ]
    }

    static List<BuildOperationRecord.Progress> filterByJavaVersion(List<BuildOperationRecord.Progress> events, JvmInstallationMetadata jdkMetadata) {
        events.findAll { it.details.toolchain.javaVersion == jdkMetadata.javaVersion }
    }

    List<BuildOperationRecord.Progress> toolchainEvents(String taskPath) {
        initIfNeeded()
        return progressEventsFor(
            operations, JavaToolchainUsageProgressDetails, taskPath,
            ResolveTaskMutationsBuildOperationType, ExecuteTaskBuildOperationType
        )
    }

    private static <T extends BuildOperationType<?, ?>> List<BuildOperationRecord.Progress> progressEventsFor(
        BuildOperationsFixture operations,
        Class<?> detailsType,
        String taskPath,
        Class<T>... buildOperationTypes
    ) {
        List<BuildOperationRecord.Progress> events = []
        for (def buildOperationType in buildOperationTypes) {
            events += progressEventsFor(operations, detailsType, taskPath, buildOperationType)
        }
        return events
    }

    private static <T extends BuildOperationType<?, ?>> List<BuildOperationRecord.Progress> progressEventsFor(
        BuildOperationsFixture operations,
        Class<?> detailsType,
        String taskPath,
        Class<T> buildOperationType
    ) {
        def buildOperationRecord = operations.first(buildOperationType) { it.details.taskPath == taskPath }
        List<BuildOperationRecord.Progress> events = []
        operations.walk(buildOperationRecord) {
            events.addAll(it.progress.findAll {
                detailsType.isAssignableFrom(it.detailsType)
            })
        }
        return events
    }
}
