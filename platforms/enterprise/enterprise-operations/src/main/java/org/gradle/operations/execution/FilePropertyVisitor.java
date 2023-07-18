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

package org.gradle.operations.execution;

import java.io.File;
import java.util.Set;

/**
 * The consuming visitor for file properties on work.
 * <p>
 * Properties are visited depth-first lexicographical.
 * Roots are visited in semantic order (i.e. the order in which they make up the file collection)
 * Files and directories are depth-first lexicographical.
 * <p>
 * For roots that are a file, they are also visited with {@link #file(VisitState)}.
 *
 * @since 8.3
 */
public interface FilePropertyVisitor {

    /**
     * Called once per file property.
     * <p>
     * Only getProperty*() state methods may be called during.
     */
    void preProperty(VisitState state);

    /**
     * Called for each root of the current property.
     * <p>
     * {@link VisitState#getName()} and {@link VisitState#getPath()} may be called during.
     */
    void preRoot(VisitState state);

    /**
     * Called before entering a directory.
     * <p>
     * {@link VisitState#getName()} and {@link VisitState#getPath()} may be called during.
     */
    void preDirectory(VisitState state);

    /**
     * Called when visiting a non-directory file.
     * <p>
     * {@link VisitState#getName()}, {@link VisitState#getPath()} and {@link VisitState#getHashBytes()} may be called during.
     */
    void file(VisitState state);

    /**
     * Called when exiting a directory.
     */
    void postDirectory();

    /**
     * Called when exiting a root.
     */
    void postRoot();

    /**
     * Called when exiting a property.
     */
    void postProperty();

    /**
     * Provides information about the current location in the visit.
     * <p>
     * Consumers should expect this to be mutable.
     * Calling any method on this outside of a method that received it has undefined behavior.
     */
    interface VisitState {
        /**
         * Returns the currently visited property name. Each property has a unique name.
         */
        String getPropertyName();

        /**
         * Returns the hash of the currently visited property.
         */
        byte[] getPropertyHashBytes();

        /**
         * A description of how the current property was fingerprinted.
         * <p>
         * Returns one or more of the values of org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute, sorted.
         * <p>
         * This interface does not constrain the compatibility of values.
         * In practice however, such constraints do exist but are managed informally.
         * For example, consumers can assume that both org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FilePropertyAttribute#DIRECTORY_SENSITIVITY_DEFAULT
         * and org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute#DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES will not be present.
         * This loose approach is used to allow the various types of normalization supported by Gradle to evolve,
         * and their usage to be conveyed here without changing this interface.
         */
        Set<String> getPropertyAttributes();

        /**
         * Returns the absolute path of the currently visited location.
         */
        String getPath();

        /**
         * Returns the name of the currently visited location, as in {@link File#getName()}
         */
        String getName();

        /**
         * Returns the normalized content hash of the last visited file.
         * <p>
         * Must not be called when the last visited location was a directory.
         */
        byte[] getHashBytes();
    }
}
