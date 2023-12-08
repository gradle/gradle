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
import org.gradle.internal.operations.trace.BuildOperationRecord

class BuildCacheOperationFixtures {

    BuildOperationsFixture buildOperations

    BuildCacheOperationFixtures(BuildOperationsFixture buildOperations) {
        this.buildOperations = buildOperations
    }

    private BuildOperationRecord getTaskCacheExecutionBuildOperationRecord(String taskPath) {
        return buildOperations.first(ExecuteTaskBuildOperationType) { it.details["taskPath"] == taskPath }
    }

    private List<BuildOperationRecord> getPackOperation(String taskPath) {
        def parent = getTaskCacheExecutionBuildOperationRecord(taskPath)
        return parent == null ? [] : buildOperations.search(parent) { it.hasDetailsOfType(PackOperationDetails) }
    }

    String getTaskCacheKey(String taskPath) {
        def packOperations = getPackOperation(taskPath)
        assert packOperations.size() > 0
        return packOperations[0].details["cacheKey"]
    }
}
