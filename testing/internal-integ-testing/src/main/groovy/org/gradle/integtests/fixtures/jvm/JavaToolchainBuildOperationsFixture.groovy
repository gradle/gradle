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
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.jvm.toolchain.internal.operations.JavaToolchainUsageProgressDetails

/**
 * Captures build operations and allows to make assertions about events related to Java Toolchains.
 * <p>
 * When using this fixture make sure to first call {@link JavaToolchainBuildOperationsFixture#captureBuildOperations captureBuildOperations}
 * in the {@code setup} method of the test class or in the beginning of a test itself.
 */
@SelfType(AbstractIntegrationSpec)
trait JavaToolchainBuildOperationsFixture {

    private BuildOperationsFixture operations

    void captureBuildOperations() {
        if (operations == null) {
            operations = new BuildOperationsFixture(executer, temporaryFolder)
        }
    }

    private void ensureInitialized() {
        if (operations == null) {
            throw new IllegalStateException("Make sure to call `captureBuildOperations` before using methods that work on captured operations.")
        }
    }

    static void assertToolchainUsages(List<BuildOperationRecord.Progress> events, JvmInstallationMetadata jdkMetadata, String tool) {
        assert events.size() > 0
        events.each { usageEvent ->
            assertToolchainUsage(tool, jdkMetadata, usageEvent)
        }
    }

    static void assertToolchainUsages(List<BuildOperationRecord.Progress> events, JvmInstallationMetadata jdkMetadata, String... tools) {
        assert events.size() > 0
        def expectedTools = tools.toList().sort()
        def usedTools = events.collect { it.details.toolName }.unique().sort()
        assert expectedTools == usedTools
        events.each { usageEvent ->
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
        ensureInitialized()
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
            events.addAll(it.progress(detailsType))
        }
        return events
    }
}
