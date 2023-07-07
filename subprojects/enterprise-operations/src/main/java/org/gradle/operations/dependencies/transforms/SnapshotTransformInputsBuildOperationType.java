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

package org.gradle.operations.dependencies.transforms;

import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.operations.execution.FilePropertyVisitor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Represents the computation of capturing the before execution state and resolving the caching state for transforms.
 * <p>
 * Must occur as a child of {@link org.gradle.operations.execution.ExecuteWorkBuildOperationType}.
 *
 * @since 8.3
 */
public final class SnapshotTransformInputsBuildOperationType implements BuildOperationType<SnapshotTransformInputsBuildOperationType.Details, SnapshotTransformInputsBuildOperationType.Result> {
    public interface Details {
    }

    /**
     * The hashes of the inputs.
     * <p>
     * If the inputs were not snapshotted, all fields are null.
     */
    public interface Result {

        /**
         * The overall hash value for the inputs.
         * <p>
         * Null if the overall key was not calculated because the inputs were invalid.
         */
        @Nullable
        byte[] getHashBytes();

        /**
         * The hash of the classloader that loaded the transform action implementation.
         * <p>
         * Null if the classloader is not managed by Gradle.
         */
        @Nullable
        byte[] getClassLoaderHashBytes();

        /**
         * The class name of the transform's action.
         */
        @Nullable
        String getImplementationClassName();

        /**
         * The input value property hashes.
         * <p>
         * key = property name
         * <p>
         * Ordered by key, lexicographically.
         * No null keys or values.
         * Never empty.
         */
        @Nullable
        Map<String, byte[]> getInputValueHashesBytes();

        /**
         * Traverses the input properties that are file types (e.g. File, FileCollection, FileTree, List of File).
         * <p>
         * If there are no input file properties, visitor will not be called at all.
         * <p>
         * This is using the visitor from {@link SnapshotTaskInputsBuildOperationType} since there is no difference
         * between tasks and transforms in this regard. Later we can unify the transform and the task build operation type.
         */
        void visitInputFileProperties(FilePropertyVisitor visitor);

        /**
         * The names of the output properties.
         * <p>
         * No duplicate values.
         * Ordered lexicographically.
         * Never empty.
         */
        @Nullable
        List<String> getOutputPropertyNames();
    }
}
