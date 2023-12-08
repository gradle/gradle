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
import org.gradle.caching.internal.controller.operations.PackOperationDetails
import org.gradle.caching.internal.controller.operations.UnpackOperationDetails
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

    List<BuildOperationRecord> getPackOperations(String taskPath) {
        def parent = getTaskCacheExecutionBuildOperationRecord(taskPath)
        return parent == null ? [] : buildOperations.search(parent) { it.hasDetailsOfType(PackOperationDetails) }
    }

    BuildOperationRecord getOnlyPackOperation(String taskPath) {
        def ops = getPackOperations(taskPath)
        assert ops.size() == 1
        return ops.first()
    }

    List<BuildOperationRecord> getUnpackOperations(String taskPath) {
        def parent = getTaskCacheExecutionBuildOperationRecord(taskPath)
        return parent == null ? [] : buildOperations.search(parent) { it.hasDetailsOfType(UnpackOperationDetails) }
    }

    BuildOperationRecord getOnlyUnpackOperations(String taskPath) {
        def ops = getUnpackOperations(taskPath)
        assert ops.size() == 1
        return ops.first()
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
}
