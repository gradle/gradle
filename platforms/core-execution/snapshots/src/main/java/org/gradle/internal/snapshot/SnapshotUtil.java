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

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableListMultimap;
import org.gradle.internal.hash.HashCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SnapshotUtil {

    public static Map<String, FileSystemLocationSnapshot> indexByAbsolutePath(FileSystemSnapshot snapshot) {
        HashMap<String, FileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(entrySnapshot -> {
            index.put(entrySnapshot.getAbsolutePath(), entrySnapshot);
            return SnapshotVisitResult.CONTINUE;
        });
        return index;
    }

    public static Map<String, FileSystemLocationSnapshot> indexByRelativePath(FileSystemSnapshot snapshot) {
        HashMap<String, FileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(new RelativePathTracker(), (entrySnapshot, relativePath) -> {
            index.put(relativePath.toRelativePath(), entrySnapshot);
            return SnapshotVisitResult.CONTINUE;
        });
        return index;
    }

    public static Map<String, FileSystemLocationSnapshot> rootIndex(FileSystemSnapshot snapshot) {
        HashMap<String, FileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(entrySnapshot -> {
            index.put(entrySnapshot.getAbsolutePath(), entrySnapshot);
            return SnapshotVisitResult.SKIP_SUBTREE;
        });
        return index;
    }

    public static <T extends FileSystemNode> Optional<MetadataSnapshot> getMetadataFromChildren(ChildMap<T> children, VfsRelativePath targetPath, CaseSensitivity caseSensitivity, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        return children.withNode(targetPath, caseSensitivity, new ChildMap.NodeHandler<T, Optional<MetadataSnapshot>>() {
            @Override
            public Optional<MetadataSnapshot> handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
                return child.getSnapshot(pathInChild, caseSensitivity);
            }

            @Override
            public Optional<MetadataSnapshot> handleAsAncestorOfChild(String childPath, T child) {
                return noChildFoundResult.get();
            }

            @Override
            public Optional<MetadataSnapshot> handleExactMatchWithChild(T child) {
                return child.getSnapshot();
            }

            @Override
            public Optional<MetadataSnapshot> handleUnrelatedToAnyChild() {
                return noChildFoundResult.get();
            }
        });
    }

    public static <T extends FileSystemNode> Optional<FileSystemNode> getChild(ChildMap<T> children, VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return children.withNode(targetPath, caseSensitivity, new ChildMap.NodeHandler<T, Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
                return child.getNode(pathInChild, caseSensitivity);
            }

            @Override
            public Optional<FileSystemNode> handleAsAncestorOfChild(String childPath, T child) {
                return Optional.of(child);
            }

            @Override
            public Optional<FileSystemNode> handleExactMatchWithChild(T child) {
                return Optional.of(child);
            }

            @Override
            public Optional<FileSystemNode> handleUnrelatedToAnyChild() {
                return Optional.empty();
            }
        });
    }

    public static ImmutableListMultimap<String, HashCode> getRootHashes(FileSystemSnapshot roots) {
        if (roots == FileSystemSnapshot.EMPTY) {
            return ImmutableListMultimap.of();
        }
        ImmutableListMultimap.Builder<String, HashCode> builder = ImmutableListMultimap.builder();
        roots.accept(snapshot -> {
            builder.put(snapshot.getAbsolutePath(), snapshot.getHash());
            return SnapshotVisitResult.SKIP_SUBTREE;
        });
        return builder.build();
    }

    /**
     * For a {@link RegularFileSnapshot} returns the file length, otherwise {@code 0}.
     */
    public static long getLength(FileSystemLocationSnapshot snapshot) {
        return snapshot.accept(new FileSystemLocationSnapshot.FileSystemLocationSnapshotTransformer<Long>() {
            @Override
            public Long visitDirectory(DirectorySnapshot directorySnapshot) {
                return 0L;
            }

            @Override
            public Long visitRegularFile(RegularFileSnapshot fileSnapshot) {
                return fileSnapshot.getMetadata().getLength();
            }

            @Override
            public Long visitMissing(MissingFileSnapshot missingSnapshot) {
                return 0L;
            }
        });
    }
}
