/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.Nullable;
import org.gradle.api.Task;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Represents the computation of the task artifact state and the task output caching state.
 *
 * This operation is executed only when the build cache is enabled.
 *
 * @since 4.0
 */
@UsedByScanPlugin
public final class SnapshotTaskInputsOperationDetails implements BuildOperationDetails<SnapshotTaskInputsOperationDetails.Result> {

    private final Task task;

    public SnapshotTaskInputsOperationDetails(Task task) {
        this.task = task;
    }

    public Task getTask() {
        return task;
    }

    public interface Result {

        @Nullable
        String getClassLoaderHash();

        // Order corresponds to task action order
        List<String> getActionClassLoaderHashes();

        SortedMap<String, String> getInputHashes();

        SortedSet<String> getOutputPropertyNames();

        String getBuildCacheKey();

    }
}
