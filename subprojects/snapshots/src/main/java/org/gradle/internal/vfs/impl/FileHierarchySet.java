/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MetadataSnapshot;

import javax.annotation.CheckReturnValue;
import java.util.Optional;

/**
 * An immutable set of directory trees. Intended to be use to efficiently determine whether a particular file is contained in a set of directories or not.
 */
public interface FileHierarchySet { // TODO rename to SnapshotHierarchy
    /**
     * The empty hierarchy.
     */
    FileHierarchySet EMPTY = new FileHierarchySet() {
        @Override
        public Optional<MetadataSnapshot> getMetadata(String path) {
            return Optional.empty();
        }

        @Override
        public FileHierarchySet update(String absolutePath, MetadataSnapshot snapshot) {
            return new DefaultFileHierarchySet(absolutePath, snapshot);
        }

        @Override
        public FileHierarchySet invalidate(String path) {
            return this;
        }
    };

    Optional<MetadataSnapshot> getMetadata(String path);

    default Optional<FileSystemLocationSnapshot> getSnapshot(String path) {
        return getMetadata(path)
            .filter(FileSystemLocationSnapshot.class::isInstance)
            .map(FileSystemLocationSnapshot.class::cast);
    }

    /**
     * Returns a set that contains the union of this set and the given directory. The set contains the directory itself, plus all its descendants.
     * @param absolutePath
     * @param snapshot
     */
    @CheckReturnValue
    FileHierarchySet update(String absolutePath, MetadataSnapshot snapshot);

    @CheckReturnValue
    FileHierarchySet invalidate(String path);
}
