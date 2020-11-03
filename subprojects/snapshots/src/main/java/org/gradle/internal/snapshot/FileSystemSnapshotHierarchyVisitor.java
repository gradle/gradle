/*
 * Copyright 2018 the original author or authors.
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

/**
 * Visitor for {@link FileSystemSnapshot}.
 */
public interface FileSystemSnapshotHierarchyVisitor {

    /**
     * Called before visiting the contents of a directory.
     */
    default void preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {}

    /**
     * Called for each regular file/directory/missing/unavailable file.
     *
     * @return whether the subtree should be visited.
     */
    SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot);

    /**
     * Called when leaving a directory.
     */
    default void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {}

    /**
     * @see java.nio.file.FileVisitResult
     */
    enum SnapshotVisitResult {

        /**
         * Continue. When returned from a {@link FileVisitor#visitEntry
         * preVisitDirectory} method then the entries in the directory should also
         * be visited.
         */
        CONTINUE,

        /**
         * Terminate.
         */
        TERMINATE,

        /**
         * Continue without visiting the entries in this directory. This result
         * is only meaningful when returned from the {@link
         * FileVisitor#preVisitDirectory preVisitDirectory} method; otherwise
         * this result type is the same as returning {@link #CONTINUE}.
         */
        SKIP_SUBTREE
    }
}
