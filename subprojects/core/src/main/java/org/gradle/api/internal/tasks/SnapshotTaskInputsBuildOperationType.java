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
import org.gradle.internal.operations.BuildOperationType;
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
public final class SnapshotTaskInputsBuildOperationType implements BuildOperationType<SnapshotTaskInputsBuildOperationType.Details, SnapshotTaskInputsBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

        /**
         * The identity path of the task.
         */
        String getTaskPath();

        /**
         * An ID for the task, that disambiguates it from other tasks with the same path.
         *
         * Due to a bug in Gradle, two tasks with the same path can be executed.
         * This is very problematic for build scans.
         * As such, scans need to be able to differentiate between different tasks with the same path.
         * The combination of the path and ID does this.
         *
         * In later versions of Gradle, executing two tasks with the same path will be prevented
         * and this value can be noop-ed.
         */
        long getTaskId();

    }

    @UsedByScanPlugin
    public interface Result {

        @Nullable
        String getClassLoaderHash();

        // Order corresponds to task action order
        List<String> getActionClassLoaderHashes();

        // Order corresponds to task action order
        List<String> getActionTypes();

        SortedMap<String, String> getInputHashes();

        SortedSet<String> getOutputPropertyNames();

        String getBuildCacheKey();

    }

    private SnapshotTaskInputsBuildOperationType() {
    }

}
