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

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Represents the computation of the task artifact state and the task output caching state.
 *
 * This operation is executed only when the build cache is enabled.
 * Must occur as a child of {@link org.gradle.internal.execution.ExecuteTaskBuildOperationType}.
 *
 * @since 4.0
 */
public final class SnapshotTaskInputsBuildOperationType implements BuildOperationType<SnapshotTaskInputsBuildOperationType.Details, SnapshotTaskInputsBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

    }

    /**
     * The hashes of the inputs.
     *
     * If the inputs were not snapshotted, all fields are null.
     * This may occur if the task had no outputs.
     */
    @UsedByScanPlugin
    public interface Result {

        /**
         * The overall hash value for the inputs.
         *
         * Null if the overall key was not calculated because the inputs were invalid.
         */
        @Nullable
        String getBuildCacheKey();

        /**
         * The hash of the the classloader that loaded the task implementation.
         *
         * Null if the classloader is not managed by Gradle.
         */
        @Nullable
        String getClassLoaderHash();

        /**
         * The hashes of the classloader that loaded each of the task's actions.
         *
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         * May contain nulls (non Gradle managed classloader)
         */
        @Nullable
        List<String> getActionClassLoaderHashes();

        /**
         * The class names of each of the task's actions.
         *
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         */
        @Nullable
        List<String> getActionClassNames();

        /**
         * Hashes of each of the input properties.
         *
         * key = property name
         * value = hash
         *
         * Ordered by key, lexicographically.
         * No null keys or values.
         * Never empty.
         * Null if the task has no inputs.
         */
        @Nullable
        Map<String, String> getInputHashes();

        /**
         * The names of the output properties.
         *
         * No duplicate values.
         * Ordered lexicographically.
         * Never empty.
         */
        @Nullable
        List<String> getOutputPropertyNames();

    }

    private SnapshotTaskInputsBuildOperationType() {
    }

}
