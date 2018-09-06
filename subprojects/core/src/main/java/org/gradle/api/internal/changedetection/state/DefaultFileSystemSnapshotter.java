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

package org.gradle.api.internal.changedetection.state;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshotFilter;
import org.gradle.api.internal.changedetection.state.mirror.MirrorUpdatingDirectoryWalker;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalMissingSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.ImmutablePatternSet;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.Factory;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for snapshotting various aspects of the file system.
 *
 * Currently logic and state are split between this class and {@link FileSystemMirror}, as there are several instances of this class created in different scopes. This introduces some inefficiencies
 * that could be improved by shuffling this relationship around.
 *
 * The implementations attempt to do 2 things: avoid doing the same work in parallel (e.g. scanning the same directory from multiple threads, and avoid doing work where the result is almost certainly
 * the same as before (e.g. don't scan the output directory of a task a bunch of times).
 *
 * The implementations are currently intentionally very, very simple, and so there are a number of ways in which they can be made much more efficient. This can happen over time.
 */
@NonNullApi
public class DefaultFileSystemSnapshotter implements FileSystemSnapshotter {
    private static final PatternSet EMPTY_PATTERN_SET = ImmutablePatternSet.of(new PatternSet());

    private final FileHasher hasher;
    private final StringInterner stringInterner;
    private final FileSystem fileSystem;
    private final FileSystemMirror fileSystemMirror;
    private final ProducerGuard<String> producingSnapshots = ProducerGuard.striped();
    private final MirrorUpdatingDirectoryWalker mirrorUpdatingDirectoryWalker;

    public DefaultFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, FileSystemMirror fileSystemMirror) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.fileSystemMirror = fileSystemMirror;
        this.mirrorUpdatingDirectoryWalker = new MirrorUpdatingDirectoryWalker(hasher, fileSystem, stringInterner);
    }

    @Override
    public boolean exists(File file) {
        FileMetadataSnapshot metadata = fileSystemMirror.getMetadata(file.getAbsolutePath());
        if (metadata != null) {
            return metadata.getType() != FileType.Missing;
        }
        PhysicalSnapshot snapshot = fileSystemMirror.getSnapshot(file.getAbsolutePath());
        if (snapshot != null) {
            return snapshot.getType() != FileType.Missing;
        }
        return file.exists();
    }

    @Override
    public HashCode getRegularFileContentHash(final File file) {
        final String absolutePath = file.getAbsolutePath();
        FileMetadataSnapshot metadata = fileSystemMirror.getMetadata(absolutePath);
        if (metadata != null) {
            if (metadata.getType() != FileType.RegularFile) {
                return null;
            }
            PhysicalSnapshot snapshot = fileSystemMirror.getSnapshot(absolutePath);
            if (snapshot != null) {
                return snapshot.getHash();
            }
        }
        return producingSnapshots.guardByKey(absolutePath, new Factory<HashCode>() {
            @Nullable
            @Override
            public HashCode create() {
                InternableString internableAbsolutePath = new InternableString(absolutePath);
                FileMetadataSnapshot metadata = statAndCache(internableAbsolutePath, file);
                if (metadata.getType() != FileType.RegularFile) {
                    return null;
                }
                PhysicalSnapshot snapshot = snapshotAndCache(internableAbsolutePath, file, metadata, null);
                return snapshot.getHash();
            }
        });
    }

    @Override
    public PhysicalSnapshot snapshot(final File file) {
        final String absolutePath = file.getAbsolutePath();
        PhysicalSnapshot result = fileSystemMirror.getSnapshot(absolutePath);
        if (result == null) {
            result = producingSnapshots.guardByKey(absolutePath, new Factory<PhysicalSnapshot>() {
                @Override
                public PhysicalSnapshot create() {
                    return snapshotAndCache(file, null);
                }
            });
        }
        return result;
    }

    @Override
    public List<FileSystemSnapshot> snapshot(FileCollectionInternal fileCollection) {
        FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl();
        fileCollection.visitRootElements(visitor);
        return visitor.getRoots();
    }

    private PhysicalSnapshot snapshotAndCache(File file, @Nullable PatternSet patternSet) {
        InternableString internableAbsolutePath = new InternableString(file.getAbsolutePath());
        FileMetadataSnapshot metadata = statAndCache(internableAbsolutePath, file);
        return snapshotAndCache(internableAbsolutePath, file, metadata, patternSet);
    }

    private FileMetadataSnapshot statAndCache(InternableString internableAbsolutePath, File file) {
        FileMetadataSnapshot metadata = fileSystemMirror.getMetadata(internableAbsolutePath.asNonInterned());
        if (metadata == null) {
            metadata = fileSystem.stat(file);
            fileSystemMirror.putMetadata(internableAbsolutePath.asInterned(), metadata);
        }
        return metadata;
    }

    private PhysicalSnapshot snapshotAndCache(InternableString internableAbsolutePath, File file, FileMetadataSnapshot metadata, @Nullable PatternSet patternSet) {
        PhysicalSnapshot physicalSnapshot = fileSystemMirror.getSnapshot(internableAbsolutePath.asNonInterned());
        if (physicalSnapshot == null) {
            MutableBoolean hasBeenFiltered = new MutableBoolean(false);
            physicalSnapshot = snapshot(internableAbsolutePath.asInterned(), patternSet, file, metadata, hasBeenFiltered);
            if (!hasBeenFiltered.get()) {
                fileSystemMirror.putSnapshot(physicalSnapshot);
            }
        }
        return physicalSnapshot;
    }

    private PhysicalSnapshot snapshot(String absolutePath, @Nullable PatternSet patternSet, File file, FileMetadataSnapshot metadata, MutableBoolean hasBeenFiltered) {
        String name = stringInterner.intern(file.getName());
        switch (metadata.getType()) {
            case Missing:
                return new PhysicalMissingSnapshot(absolutePath, name);
            case RegularFile:
                return new PhysicalFileSnapshot(absolutePath, name, hasher.hash(file, metadata), metadata.getLastModified());
            case Directory:
                return mirrorUpdatingDirectoryWalker.walkDir(absolutePath, patternSet, hasBeenFiltered);
            default:
                throw new IllegalArgumentException("Unrecognized file type: " + metadata.getType());
        }
    }

    /*
     * For simplicity this only caches trees without includes/excludes. However, if it is asked
     * to snapshot a filtered tree, it will try to find a snapshot for the underlying
     * tree and filter it in memory instead of walking the file system again. This covers the
     * majority of cases, because all task outputs are put into the cache without filters
     * before any downstream task uses them.
     *
     * If it turns out that a filtered tree has actually not been filtered (i.e. the condition always returned true),
     * then we cache the result as unfiltered tree.
     */
    @VisibleForTesting
    FileSystemSnapshot snapshotDirectoryTree(final DirectoryFileTree dirTree) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = dirTree.getDir().getAbsolutePath();
        final PatternSet patterns = dirTree.getPatterns();

        PhysicalSnapshot snapshot = fileSystemMirror.getSnapshot(path);
        if (snapshot != null) {
            return filterSnapshot(snapshot, patterns);
        }
        return producingSnapshots.guardByKey(path, new Factory<FileSystemSnapshot>() {
            @Override
            public FileSystemSnapshot create() {
                PhysicalSnapshot snapshot = fileSystemMirror.getSnapshot(path);
                if (snapshot == null) {
                    snapshot = snapshotAndCache(dirTree.getDir(), patterns);
                    return filterSnapshot(snapshot, EMPTY_PATTERN_SET);
                } else {
                    return filterSnapshot(snapshot, patterns);
                }
            }
        });
    }

    private FileSystemSnapshot snapshotTree(final FileTreeInternal tree) {
        final FileSystemSnapshotBuilder builder = new FileSystemSnapshotBuilder(stringInterner);
        tree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                builder.addDir(dirDetails.getFile(), dirDetails.getRelativePath().getSegments());
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                builder.addFile(fileDetails.getFile(), fileDetails.getRelativePath().getSegments(), physicalFileSnapshot(fileDetails));
            }

            private PhysicalFileSnapshot physicalFileSnapshot(FileVisitDetails fileDetails) {
                return new PhysicalFileSnapshot(stringInterner.intern(fileDetails.getFile().getAbsolutePath()), fileDetails.getName(), hasher.hash(fileDetails), fileDetails.getLastModified());
            }
        });
        return builder.build();
    }

    private FileSystemSnapshot filterSnapshot(PhysicalSnapshot snapshot, PatternSet patterns) {
        if (snapshot.getType() == FileType.Missing) {
            return FileSystemSnapshot.EMPTY;
        }
        if (patterns.isEmpty()) {
            return snapshot;
        }
        Spec<FileTreeElement> spec = patterns.getAsSpec();
        return FileSystemSnapshotFilter.filterSnapshot(spec, snapshot, fileSystem);
    }

    private class FileCollectionVisitorImpl implements FileCollectionVisitor {
        private final List<FileSystemSnapshot> roots = new ArrayList<FileSystemSnapshot>();

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                PhysicalSnapshot fileSnapshot = snapshot(file);
                roots.add(fileSnapshot);
            }
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            FileSystemSnapshot treeSnapshot = snapshotTree(fileTree);
            roots.add(treeSnapshot);
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryTree) {
            FileSystemSnapshot treeSnapshot = snapshotDirectoryTree(directoryTree);
            roots.add(treeSnapshot);
        }

        public List<FileSystemSnapshot> getRoots() {
            return roots;
        }
    }

    private class InternableString {
        private String string;
        private boolean interned;

        public InternableString(String nonInternedString) {
            this.string = nonInternedString;
        }

        public String asInterned() {
            if (!interned)  {
                interned = true;
                string = stringInterner.intern(string);
            }
            return string;
        }

        public String asNonInterned() {
            return string;
        }
    }
}
