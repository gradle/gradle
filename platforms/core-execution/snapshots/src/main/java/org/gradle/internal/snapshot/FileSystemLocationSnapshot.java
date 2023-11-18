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

import com.google.common.collect.Interner;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.hash.HashCode;

import java.util.Comparator;

/**
 * A snapshot of a single location on the file system.
 *
 * We know everything about this snapshot, including children and Merkle hash.
 *
 * The snapshot can be a snapshot of a regular file or of a whole directory tree.
 * The file at the location is not required to exist (see {@link MissingFileSnapshot}.
 */
public interface FileSystemLocationSnapshot extends FileSystemSnapshot, FileSystemNode, MetadataSnapshot {

    /**
     * The comparator of direct children of a file system location.
     *
     * The comparison is stable with respect to case sensitivity, so the order of the children is stable across operating systems.
     */
    Comparator<FileSystemLocationSnapshot> BY_NAME = Comparator.comparing(FileSystemLocationSnapshot::getName, PathUtil::compareFileNames);

    /**
     * The file name.
     */
    String getName();

    /**
     * The absolute path of the file.
     */
    String getAbsolutePath();

    /**
     * Constructs a copy of this snapshot with a new absolute path.
     */
    FileSystemLocationSnapshot relocate(String targetPath, Interner<String> interner);

    /**
     * The hash of the snapshot.
     *
     * This makes it possible to uniquely identify the snapshot.
     * <dl>
     *     <dt>Directories</dt>
     *     <dd>The combined hash of the children, calculated by appending the name and the hash of each child to a hasher.</dd>
     *     <dt>Regular Files</dt>
     *     <dd>The hash of the content of the file.</dd>
     *     <dt>Missing files</dt>
     *     <dd>A special signature denoting a missing file.</dd>
     * </dl>
     */
    HashCode getHash();

    /**
     * Whether the content and the metadata (modification date) of the current snapshot is the same as for the given one.
     */
    boolean isContentAndMetadataUpToDate(FileSystemLocationSnapshot other);

    /**
     * Whether the content of the current snapshot is the same as for the given one.
     */
    boolean isContentUpToDate(FileSystemLocationSnapshot other);

    /**
     * Whether the file system location represented by this snapshot is a symlink or not.
     */
    FileMetadata.AccessType getAccessType();

    void accept(FileSystemLocationSnapshotVisitor visitor);
    <T> T accept(FileSystemLocationSnapshotTransformer<T> transformer);

    interface FileSystemLocationSnapshotVisitor {
        default void visitDirectory(DirectorySnapshot directorySnapshot) {};
        default void visitRegularFile(RegularFileSnapshot fileSnapshot) {};
        default void visitMissing(MissingFileSnapshot missingSnapshot) {};
    }

    interface FileSystemLocationSnapshotTransformer<T> {
        T visitDirectory(DirectorySnapshot directorySnapshot);
        T visitRegularFile(RegularFileSnapshot fileSnapshot);
        T visitMissing(MissingFileSnapshot missingSnapshot);
    }
}
