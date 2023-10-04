/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.snapshot;

import org.gradle.internal.file.FileMetadata;

import javax.annotation.Nullable;

/**
 * A builder for {@link DirectorySnapshot}.
 *
 * In order to build a directory snapshot, you need to call the methods for entering/leaving a directory
 * and for visiting leaf elements.
 * The visit methods need to be called in depth-first order.
 * When leaving a directory, the builder will create a {@link DirectorySnapshot} for the directory,
 * calculating the combined hash of the entries.
 */
public interface DirectorySnapshotBuilder {

    /**
     * Convenience method for {@link #enterDirectory(FileMetadata.AccessType, String, String, EmptyDirectoryHandlingStrategy)}
     * when you already have a {@link DirectorySnapshot}.
     */
    default void enterDirectory(DirectorySnapshot directorySnapshot, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy) {
        enterDirectory(directorySnapshot.getAccessType(), directorySnapshot.getAbsolutePath(), directorySnapshot.getName(), emptyDirectoryHandlingStrategy);
    }

    /**
     * Method to call before visiting all the entries of a directory.
     */
    void enterDirectory(FileMetadata.AccessType accessType, String absolutePath, String name, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy);

    void visitLeafElement(FileSystemLeafSnapshot snapshot);

    void visitDirectory(DirectorySnapshot directorySnapshot);

    /**
     * Method to call after having visited all the entries of a directory.
     *
     * May return {@code null} when the directory is empty and {@link EmptyDirectoryHandlingStrategy#EXCLUDE_EMPTY_DIRS}
     * has been used when calling {@link #enterDirectory(FileMetadata.AccessType, String, String, EmptyDirectoryHandlingStrategy)}.
     * This means that the directory will not be part of the built snapshot.
     */
    @Nullable
    FileSystemLocationSnapshot leaveDirectory();

    /**
     * Returns the snapshot for the root directory.
     *
     * May return null if
     * - nothing was visited, or
     * - only empty directories have been visited with {@link EmptyDirectoryHandlingStrategy#EXCLUDE_EMPTY_DIRS}.
     */
    @Nullable
    FileSystemLocationSnapshot getResult();

    enum EmptyDirectoryHandlingStrategy {
        INCLUDE_EMPTY_DIRS,
        EXCLUDE_EMPTY_DIRS
    }
}
