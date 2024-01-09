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

package org.gradle.integtests.fixtures

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheArchivePackBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheArchiveUnpackBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheLocalLoadBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheLocalStoreBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheRemoteStoreBuildOperationType
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.file.TestDirectoryProvider

class BuildCacheOperationFixtures {

    BuildOperationsFixture buildOperations

    BuildCacheOperationFixtures(GradleExecuter executer, TestDirectoryProvider projectDir) {
        this(new BuildOperationsFixture(executer, projectDir))
    }

    BuildCacheOperationFixtures(BuildOperationsFixture buildOperations) {
        this.buildOperations = buildOperations
    }

    private BuildOperationRecord getExecutionBuildOperationRecordForTask(String taskPath) {
        return buildOperations.first(ExecuteTaskBuildOperationType) { it.details["taskPath"] == taskPath }
    }

    private List<BuildOperationRecord> getOperationsForTask(String taskPath, Class<?> details) {
        def parent = getExecutionBuildOperationRecordForTask(taskPath)
        return parent == null ? [] : buildOperations.search(parent) { it.hasDetailsOfType(details) }
    }

    private BuildOperationRecord getOnlyOperationForTask(String taskPath, Class<?> details) {
        def ops = getOperationsForTask(taskPath, details)
        assert ops.size() == 1
        return ops.first()
    }

    List<BuildOperationRecord> getPackOperationsForTask(String taskPath) {
        return getOperationsForTask(taskPath, BuildCacheArchivePackBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyPackOperationForTask(String taskPath) {
        return getOnlyOperationForTask(taskPath, BuildCacheArchivePackBuildOperationType.Details)
    }

    List<BuildOperationRecord> getLocalLoadOperationsForTask(String taskPath) {
        return getOperationsForTask(taskPath, BuildCacheLocalLoadBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyLocalLoadOperationForTask(String taskPath) {
        return getOnlyOperationForTask(taskPath, BuildCacheLocalLoadBuildOperationType.Details)
    }

    List<BuildOperationRecord> getLocalStoreOperationsForTask(String taskPath) {
        return getOperationsForTask(taskPath, BuildCacheLocalStoreBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyLocalStoreOperationForTask(String taskPath) {
        return getOnlyOperationForTask(taskPath, BuildCacheLocalStoreBuildOperationType.Details)
    }

    List<BuildOperationRecord> getUnpackOperationsForTask(String taskPath) {
        return getOperationsForTask(taskPath, BuildCacheArchiveUnpackBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyUnpackOperationForTask(String taskPath) {
        return getOnlyOperationForTask(taskPath, BuildCacheArchiveUnpackBuildOperationType.Details)
    }

    List<BuildOperationRecord> getRemoteStoreOperationsForTask(String taskPath) {
        return getOperationsForTask(taskPath, BuildCacheRemoteStoreBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyRemoteStoreOperationForTask(String taskPath) {
        return getOnlyOperationForTask(taskPath, BuildCacheRemoteStoreBuildOperationType.Details)
    }

    String getCacheKeyForTaskOrNull(String taskPath) {
        def packOperations = getPackOperationsForTask(taskPath)
        return packOperations.empty ? null : packOperations[0].details["cacheKey"]
    }

    String getCacheKeyForTask(String taskPath) {
        def cacheKey = getCacheKeyForTaskOrNull(taskPath)
        assert cacheKey != null
        return cacheKey
    }

    void assertStoredToLocalCacheForTask(String taskPath) {
        def operations = getLocalStoreOperationsForTask(taskPath)
        assert !operations.empty && operations[0].result["stored"] == true
    }

    void assertNotStoredToLocalCacheForTask(String taskPath) {
        def operations = getLocalStoreOperationsForTask(taskPath)
        assert operations.empty
    }

    void assertStoredToRemoteCacheForTask(String taskPath) {
        def operations = getRemoteStoreOperationsForTask(taskPath)
        assert !operations.empty && operations[0].result["stored"] == true
    }

    void assertNotStoredToRemoteCacheForTask(String taskPath) {
        def operations = getRemoteStoreOperationsForTask(taskPath)
        assert operations.empty
    }
}
