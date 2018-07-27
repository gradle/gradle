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

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshotFilter;
import org.gradle.api.internal.changedetection.state.mirror.MirrorUpdatingDirectoryWalker;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalMissingSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.Factory;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.normalization.internal.InputNormalizationStrategy;

import java.io.File;

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
    private final FileHasher hasher;
    private final StringInterner stringInterner;
    private final FileSystem fileSystem;
    private final FileSystemMirror fileSystemMirror;
    private final ProducerGuard<String> producingSelfSnapshots = ProducerGuard.striped();
    private final ProducerGuard<String> producingTrees = ProducerGuard.striped();
    private final ProducerGuard<String> producingAllSnapshots = ProducerGuard.striped();
    private final AbsolutePathFileCollectionFingerprinter fingerprinter;
    private final MirrorUpdatingDirectoryWalker mirrorUpdatingDirectoryWalker;

    public DefaultFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.fileSystemMirror = fileSystemMirror;
        this.fingerprinter = new AbsolutePathFileCollectionFingerprinter(stringInterner, directoryFileTreeFactory, this);
        this.mirrorUpdatingDirectoryWalker = new MirrorUpdatingDirectoryWalker(hasher, fileSystem, stringInterner);
    }

    @Override
    public boolean exists(File file) {
        PhysicalSnapshot snapshot = fileSystemMirror.getFile(file.getAbsolutePath());
        if (snapshot != null) {
            return snapshot.getType() != FileType.Missing;
        }
        return file.exists();
    }

    @Override
    public PhysicalSnapshot snapshotSelf(final File file) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = file.getAbsolutePath();
        return producingSelfSnapshots.guardByKey(path, new Factory<PhysicalSnapshot>() {
            @Override
            public PhysicalSnapshot create() {
                PhysicalSnapshot snapshot = fileSystemMirror.getFile(path);
                if (snapshot == null) {
                    snapshot = calculateDetails(file);
                    fileSystemMirror.putFile(snapshot);
                }
                return snapshot;
            }
        });
    }

    @Override
    public HashCode snapshotAll(final File file) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = file.getAbsolutePath();
        return producingAllSnapshots.guardByKey(path, new Factory<HashCode>() {
            @Override
            public HashCode create() {
                HashCode fileContentHash = fileSystemMirror.getContent(path);
                if (fileContentHash == null) {
                    CurrentFileCollectionFingerprint fileCollectionFingerprint = fingerprinter.fingerprint(ImmutableFileCollection.of(file), InputNormalizationStrategy.NO_NORMALIZATION);
                    fileContentHash = fileCollectionFingerprint.getHash();
                    String internedPath = internPath(file);
                    fileSystemMirror.putContent(internedPath, fileContentHash);
                }
                return fileContentHash;
            }
        });
    }

    /*
     * For simplicity this only caches trees without includes/excludes. However, if it is asked
     * to snapshot a filtered tree, it will try to find a snapshot for the underlying
     * tree and filter it in memory instead of walking the file system again. This covers the
     * majority of cases, because all task outputs are put into the cache without filters
     * before any downstream task uses them.
     */
    @Override
    public FileSystemSnapshot snapshotDirectoryTree(final DirectoryFileTree dirTree) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = dirTree.getDir().getAbsolutePath();
        final PatternSet patterns = dirTree.getPatterns();

        FileSystemSnapshot snapshot = fileSystemMirror.getDirectoryTree(path);
        if (snapshot != null) {
            return filterSnapshot(snapshot, patterns);
        }
        return producingTrees.guardByKey(path, new Factory<FileSystemSnapshot>() {
            @Override
            public FileSystemSnapshot create() {
                FileSystemSnapshot snapshot = fileSystemMirror.getDirectoryTree(path);
                if (snapshot == null) {
                    return snapshotAndCache(dirTree, patterns);
                } else {
                    return filterSnapshot(snapshot, patterns);
                }
            }
        });
    }

    @Override
    public FileSystemSnapshot snapshotTree(final FileTreeInternal tree) {
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

    private FileSystemSnapshot snapshotAndCache(DirectoryFileTree directoryTree, PatternSet patterns) {
        PhysicalSnapshot fileSnapshot = snapshotSelf(directoryTree.getDir());
        MutableBoolean hasBeenFiltered = new MutableBoolean(false);
        FileSystemSnapshot visitableDirectoryTree = mirrorUpdatingDirectoryWalker.walk(fileSnapshot, patterns, hasBeenFiltered);
        if (!hasBeenFiltered.get()) {
            fileSystemMirror.putDirectory(fileSnapshot.getAbsolutePath(), visitableDirectoryTree);
        }
        return visitableDirectoryTree;
    }

    private FileSystemSnapshot filterSnapshot(FileSystemSnapshot snapshot, PatternSet patterns) {
        if (patterns.isEmpty()) {
            return snapshot;
        }
        Spec<FileTreeElement> spec = patterns.getAsSpec();
        return FileSystemSnapshotFilter.filterSnapshot(spec, snapshot, fileSystem);
    }

    private String internPath(File file) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    private PhysicalSnapshot calculateDetails(File file) {
        String path = internPath(file);
        FileMetadataSnapshot stat = fileSystem.stat(file);
        String name = file.getName();
        switch (stat.getType()) {
            case Missing:
                return new PhysicalMissingSnapshot(path, name);
            case Directory:
                return new PhysicalDirectorySnapshot(path, name, ImmutableList.<PhysicalSnapshot>of(), PhysicalDirectorySnapshot.SIGNATURE);
            case RegularFile:
                return new PhysicalFileSnapshot(path, name, hasher.hash(file, stat), stat.getLastModified());
            default:
                throw new IllegalArgumentException("Unrecognized file type: " + stat.getType());
        }
    }
}
