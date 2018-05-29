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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.FileSnapshotHelper;
import org.gradle.api.internal.changedetection.state.mirror.HierarchicalFileTreeVisitor;
import org.gradle.api.internal.changedetection.state.mirror.MirrorUpdatingDirectoryWalker;
import org.gradle.api.internal.changedetection.state.mirror.MutablePhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileTreeVisitor;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.VisitableDirectoryTree;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factory;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.normalization.internal.InputNormalizationStrategy;

import java.io.File;
import java.nio.file.Path;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

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
@SuppressWarnings("Since15")
@NonNullApi
public class DefaultFileSystemSnapshotter implements FileSystemSnapshotter {
    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);
    private final FileHasher hasher;
    private final StringInterner stringInterner;
    private final FileSystem fileSystem;
    private final FileSystemMirror fileSystemMirror;
    private final ProducerGuard<String> producingSelfSnapshots = ProducerGuard.striped();
    private final ProducerGuard<String> producingTrees = ProducerGuard.striped();
    private final ProducerGuard<String> producingAllSnapshots = ProducerGuard.striped();
    private final DefaultGenericFileCollectionSnapshotter snapshotter;
    private final MirrorUpdatingDirectoryWalker mirrorUpdatingDirectoryWalker;

    public DefaultFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.fileSystemMirror = fileSystemMirror;
        this.snapshotter = new DefaultGenericFileCollectionSnapshotter(stringInterner, directoryFileTreeFactory, this);
        this.mirrorUpdatingDirectoryWalker = new MirrorUpdatingDirectoryWalker(hasher, fileSystem);
    }

    @Override
    public boolean exists(File file) {
        FileSnapshot snapshot = fileSystemMirror.getFile(file.getAbsolutePath());
        if (snapshot != null) {
            return snapshot.getType() != FileType.Missing;
        }
        return file.exists();
    }

    @Override
    public FileSnapshot snapshotSelf(final File file) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = file.getAbsolutePath();
        return producingSelfSnapshots.guardByKey(path, new Factory<FileSnapshot>() {
            @Override
            public FileSnapshot create() {
                FileSnapshot snapshot = fileSystemMirror.getFile(path);
                if (snapshot == null) {
                    snapshot = calculateDetails(file);
                    fileSystemMirror.putFile(snapshot);
                }
                return snapshot;
            }
        });
    }

    @Override
    public Snapshot snapshotAll(final File file) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = file.getAbsolutePath();
        return producingAllSnapshots.guardByKey(path, new Factory<Snapshot>() {
            @Override
            public Snapshot create() {
                Snapshot snapshot = fileSystemMirror.getContent(path);
                if (snapshot == null) {
                    FileCollectionSnapshot fileCollectionSnapshot = snapshotter.snapshot(ImmutableFileCollection.of(file), InputPathNormalizationStrategy.ABSOLUTE, InputNormalizationStrategy.NOT_CONFIGURED);
                    DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
                    fileCollectionSnapshot.appendToHasher(hasher);
                    HashCode hashCode = hasher.hash();
                    snapshot = new HashBackedSnapshot(hashCode);
                    String internedPath = internPath(file);
                    fileSystemMirror.putContent(internedPath, snapshot);
                }
                return snapshot;
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
    public VisitableDirectoryTree snapshotDirectoryTree(final DirectoryFileTree dirTree) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = dirTree.getDir().getAbsolutePath();
        final PatternSet patterns = dirTree.getPatterns();

        VisitableDirectoryTree snapshot = fileSystemMirror.getDirectoryTree(path);
        if (snapshot != null) {
            return filterSnapshot(snapshot, patterns);
        }
        if (!patterns.isEmpty()) {
            return snapshotWithoutCaching(dirTree);
        }
        return producingTrees.guardByKey(path, new Factory<VisitableDirectoryTree>() {
            @Override
            public VisitableDirectoryTree create() {
                VisitableDirectoryTree snapshot = fileSystemMirror.getDirectoryTree(path);
                if (snapshot == null) {
                    return snapshotAndCache(dirTree);
                } else {
                    return snapshot;
                }
            }
        });
    }

    @Override
    public VisitableDirectoryTree snapshotTree(final FileTreeInternal tree) {
        return new VisitableDirectoryTree() {
            @Override
            public void visit(final PhysicalFileTreeVisitor visitor) {
                tree.visitTreeOrBackingFile(new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                        visitor.visit(dirDetails.getFile().toPath(), internPath(dirDetails.getFile()), dirDetails.getName(), RelativePath.EMPTY_ROOT, DirContentSnapshot.INSTANCE);
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails) {
                        String basePath = internPath(fileDetails.getFile());
                        String relativePath = PATH_JOINER.join(fileDetails.getRelativePath());
                        if (basePath.endsWith(relativePath) && (basePath.charAt(basePath.length() - relativePath.length() - 1) == File.separatorChar)) {
                            basePath =  stringInterner.intern(basePath.substring(0, basePath.length() - relativePath.length() - 1));
                            visitor.visit(fileDetails.getFile().toPath(), basePath, fileDetails.getName(), fileDetails.getRelativePath(), fileSnapshot(fileDetails));
                        } else {
                            visitor.visit(fileDetails.getFile().toPath(), basePath, fileDetails.getName(), RelativePath.EMPTY_ROOT, fileSnapshot(fileDetails));
                        }
                    }
                });
            }

            @Override
            public void accept(HierarchicalFileTreeVisitor visitor) {
                final AtomicReference<PhysicalSnapshot> root = new AtomicReference<PhysicalSnapshot>();
                tree.visitTreeOrBackingFile(new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                        PhysicalSnapshot rootSnapshot = root.get();
                        if (rootSnapshot == null) {
                            File rootFile = dirDetails.getFile();
                            for (String ignored : dirDetails.getRelativePath().getSegments()) {
                                rootFile = rootFile.getParentFile();
                            }
                            rootSnapshot = physicalDirectorySnapshot(rootFile);
                            root.set(rootSnapshot);
                        }
                        rootSnapshot.add(dirDetails.getRelativePath().getSegments(), 0, physicalDirectorySnapshot(dirDetails.getFile()));
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails) {
                        PhysicalSnapshot rootSnapshot = root.get();
                        if (rootSnapshot == null) {
                            File rootFile = fileDetails.getFile();
                            for (String ignored : fileDetails.getRelativePath().getSegments()) {
                                rootFile = rootFile.getParentFile();
                            }
                            rootSnapshot = fileDetails.getRelativePath().length() == 0 ? physicalFileSnapshot(fileDetails) : physicalDirectorySnapshot(rootFile);
                            root.set(rootSnapshot);
                        }
                        rootSnapshot.add(fileDetails.getRelativePath().getSegments(), 0, physicalFileSnapshot(fileDetails));
                    }

                    private MutablePhysicalDirectorySnapshot physicalDirectorySnapshot(File file) {
                        return new MutablePhysicalDirectorySnapshot(file.toPath(), file.getName());
                    }

                    private PhysicalFileSnapshot physicalFileSnapshot(FileVisitDetails fileDetails) {
                        FileHashSnapshot snapshot = fileSnapshot(fileDetails);
                        return new PhysicalFileSnapshot(fileDetails.getFile().toPath(), fileDetails.getName(), snapshot.getLastModified(), snapshot.getContentMd5());
                    }
                });
                PhysicalSnapshot rootSnapshot = root.get();
                if (rootSnapshot != null) {
                    rootSnapshot.accept(visitor);
                }
            }
        };
    }

    @SuppressWarnings("Since15")
    private VisitableDirectoryTree snapshotAndCache(DirectoryFileTree directoryTree) {
        final FileSnapshot fileSnapshot = snapshotSelf(directoryTree.getDir());
        VisitableDirectoryTree visitableDirectoryTree = mirrorUpdatingDirectoryWalker.walkDir(fileSnapshot);
        fileSystemMirror.putDirectory(fileSnapshot.getPath(), visitableDirectoryTree);
        return visitableDirectoryTree;
    }

    /*
     * We don't reuse code between this and #snapshotAndCache, because we can avoid
     * some defensive copying when the result won't be shared.
     */
    private VisitableDirectoryTree snapshotWithoutCaching(DirectoryFileTree directoryTree) {
        return mirrorUpdatingDirectoryWalker.walkDir(snapshotSelf(directoryTree.getDir()), directoryTree.getPatterns());
    }

    private VisitableDirectoryTree filterSnapshot(final VisitableDirectoryTree snapshot, PatternSet patterns) {
        if (patterns.isEmpty()) {
            return snapshot;
        }
        final Spec<FileTreeElement> spec = patterns.getAsSpec();
        return new VisitableDirectoryTree() {
            @Override
            public void visit(final PhysicalFileTreeVisitor visitor) {
                snapshot.visit(new PhysicalFileTreeVisitor() {
                    @Override
                    public void visit(Path path, String basePath, String name, Iterable<String> relativePath, FileContentSnapshot content) {
                        if (spec.isSatisfiedBy(new SnapshotFileTreeElement(FileSnapshotHelper.create(path, relativePath, content), fileSystem))) {
                            visitor.visit(path, basePath, name, relativePath, content);
                        }
                    }
                });
            }

            @Override
            public void accept(final HierarchicalFileTreeVisitor visitor) {
                snapshot.accept(new HierarchicalFileTreeVisitor() {
                    private Deque<String> relativePath = Lists.newLinkedList();

                    @Override
                    public void preVisitDirectory(Path path, String name) {
                        relativePath.addLast(name);
                        visitor.preVisitDirectory(path, name);
                    }

                    @Override
                    public void visit(Path path, String name, FileContentSnapshot content) {
                        if (spec.isSatisfiedBy(new SnapshotFileTreeElement(FileSnapshotHelper.create(path, relativePath, content), fileSystem))) {
                            visitor.visit(path, name, content);
                        }
                    }

                    @Override
                    public void postVisitDirectory() {
                        relativePath.removeLast();
                        visitor.postVisitDirectory();
                    }
                });
            }
        };
    }

    private String internPath(File file) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    private FileSnapshot calculateDetails(File file) {
        String path = internPath(file);
        FileMetadataSnapshot stat = fileSystem.stat(file);
        switch (stat.getType()) {
            case Missing:
                return new MissingFileSnapshot(path, new RelativePath(true, file.getName()));
            case Directory:
                return new DirectoryFileSnapshot(path, new RelativePath(false, file.getName()), true);
            case RegularFile:
                return new RegularFileSnapshot(path, new RelativePath(true, file.getName()), true, fileSnapshot(file, stat));
            default:
                throw new IllegalArgumentException("Unrecognized file type: " + stat.getType());
        }
    }

    private FileHashSnapshot fileSnapshot(FileTreeElement fileDetails) {
        return new FileHashSnapshot(hasher.hash(fileDetails), fileDetails.getLastModified());
    }

    private FileHashSnapshot fileSnapshot(File file, FileMetadataSnapshot fileDetails) {
        return new FileHashSnapshot(hasher.hash(file, fileDetails), fileDetails.getLastModified());
    }

    private static class HashBackedSnapshot implements Snapshot {
        private final HashCode hashCode;

        HashBackedSnapshot(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public void appendToHasher(BuildCacheHasher hasher) {
            hasher.putHash(hashCode);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            HashBackedSnapshot that = (HashBackedSnapshot) o;

            return hashCode.equals(that.hashCode);
        }

        @Override
        public int hashCode() {
            return hashCode.hashCode();
        }
    }
}
