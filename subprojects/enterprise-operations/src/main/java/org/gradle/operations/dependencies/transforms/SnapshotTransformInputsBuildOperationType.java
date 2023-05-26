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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public final class SnapshotTransformInputsBuildOperationType implements BuildOperationType<SnapshotTransformInputsBuildOperationType.Details, SnapshotTransformInputsBuildOperationType.Result> {
    public interface Details {
    }

    public interface Result {

        /**
         * The overall hash value for the inputs.
         * <p>
         * Null if the overall key was not calculated because the inputs were invalid.
         */
        @Nullable
        byte[] getHashBytes();

        /**
         * The hash of the classloader that loaded the task implementation.
         * <p>
         * Null if the classloader is not managed by Gradle.
         */
        @Nullable
        byte[] getClassLoaderHashBytes();


        /**
         * The hashes of the classloader that loaded each of the task's actions.
         * <p>
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         * May contain nulls (non Gradle managed classloader)
         */
        @Nullable
        List<byte[]> getActionClassLoaderHashesBytes();

        /**
         * The class names of each of the task's actions.
         * <p>
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         */
        @Nullable
        List<String> getActionClassNames();

        @Nullable
        Map<String, byte[]> getInputValueHashesBytes();

        /**
         * Traverses the input properties that are file types (e.g. File, FileCollection, FileTree, List of File).
         * <p>
         * If there are no input file properties, visitor will not be called at all.
         */
        void visitInputFileProperties(SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor visitor);

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
