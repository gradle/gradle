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
        this.buildOperations = new BuildOperationsFixture(executer, projectDir)
    }

    BuildCacheOperationFixtures(BuildOperationsFixture buildOperations) {
        this.buildOperations = buildOperations
    }

    private BuildOperationRecord getTaskCacheExecutionBuildOperationRecord(String taskPath) {
        return buildOperations.first(ExecuteTaskBuildOperationType) { it.details["taskPath"] == taskPath }
    }

    private List<BuildOperationRecord> getOperations(String taskPath, Class<?> details) {
        def parent = getTaskCacheExecutionBuildOperationRecord(taskPath)
        return parent == null ? [] : buildOperations.search(parent) { it.hasDetailsOfType(details) }
    }

    private BuildOperationRecord getOnlyOperation(String taskPath, Class<?> details) {
        def ops = getOperations(taskPath, details)
        assert ops.size() == 1
        return ops.first()
    }

    List<BuildOperationRecord> getPackOperations(String taskPath) {
        return getOperations(taskPath, BuildCacheArchivePackBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyPackOperation(String taskPath) {
        return getOnlyOperation(taskPath, BuildCacheArchivePackBuildOperationType.Details)
    }

    List<BuildOperationRecord> getLocalLoadOperations(String taskPath) {
        return getOperations(taskPath, BuildCacheLocalLoadBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyLocalLoadOperation(String taskPath) {
        return getOnlyOperation(taskPath, BuildCacheLocalLoadBuildOperationType.Details)
    }

    List<BuildOperationRecord> getLocalStoreOperations(String taskPath) {
        return getOperations(taskPath, BuildCacheLocalStoreBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyLocalStoreOperation(String taskPath) {
        return getOnlyOperation(taskPath, BuildCacheLocalStoreBuildOperationType.Details)
    }

    List<BuildOperationRecord> getUnpackOperations(String taskPath) {
        return getOperations(taskPath, BuildCacheArchiveUnpackBuildOperationType.Details)
    }

    BuildOperationRecord getOnlyUnpackOperations(String taskPath) {
        return getOnlyOperation(taskPath, BuildCacheArchiveUnpackBuildOperationType.Details)
    }

    String getTaskCacheKeyOrNull(String taskPath) {
        def packOperations = getPackOperations(taskPath)
        return packOperations.empty ? null : packOperations[0].details["cacheKey"]
    }

    String getTaskCacheKey(String taskPath) {
        def cacheKey = getTaskCacheKeyOrNull(taskPath)
        assert cacheKey != null
        return cacheKey
    }

    /**
     * TODO: Use store operations once operations are implemented
     */
    void assertStoredToLocalCache(String taskPath) {
        throw new UnsupportedOperationException("Not implemented yet")
    }

    /**
     * TODO: Use store operations once operations are implemented
     */
    void assertNotStoredToLocalCache(String taskPath) {
        throw new UnsupportedOperationException("Not implemented yet")
    }

    void assertStoredToRemoteCache(String taskPath) {
        def parent = getTaskCacheExecutionBuildOperationRecord(taskPath)
        def buildOperations = buildOperations.all(BuildCacheRemoteStoreBuildOperationType) { it.parentId == parent.id}
        assert !buildOperations.empty && buildOperations[0].result["stored"] == true
    }

    void assertNotStoredToRemoteCache(String taskPath) {
        def parent = getTaskCacheExecutionBuildOperationRecord(taskPath)
        def buildOperations = buildOperations.all(BuildCacheRemoteStoreBuildOperationType) { it.parentId == parent.id}
        assert buildOperations.empty
    }
}
