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
import com.google.common.collect.Lists;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.normalization.internal.InputNormalizationStrategy;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

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
public class DefaultFileSystemSnapshotter implements FileSystemSnapshotter, Closeable {
    private final FileHasher hasher;
    private final StringInterner stringInterner;
    private final FileSystem fileSystem;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileSystemMirror fileSystemMirror;
    private final ProducerGuard<String> producingSelfSnapshots = ProducerGuard.adaptive();
    private final ProducerGuard<String> producingTrees = ProducerGuard.adaptive();
    private final ProducerGuard<String> producingAllSnapshots = ProducerGuard.adaptive();
    private final DefaultGenericFileCollectionSnapshotter snapshotter;
    private final ExecutorService executorService;

    public DefaultFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileSystemMirror = fileSystemMirror;
        snapshotter = new DefaultGenericFileCollectionSnapshotter(stringInterner, directoryFileTreeFactory, this);
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "File system snapshotting");
            }
        });
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
                    FileCollectionSnapshot fileCollectionSnapshot = snapshotter.snapshot(new SimpleFileCollection(file), InputPathNormalizationStrategy.ABSOLUTE, InputNormalizationStrategy.NOT_CONFIGURED);
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

    @Override
    public FileTreeSnapshot snapshotDirectoryTree(final File dir) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        final String path = dir.getAbsolutePath();
        return producingTrees.guardByKey(path, new Factory<FileTreeSnapshot>() {
            @Override
            public FileTreeSnapshot create() {
                FileTreeSnapshot snapshot = fileSystemMirror.getDirectoryTree(path);
                if (snapshot == null) {
                    // Scan the directory
                    snapshot = doSnapshot(directoryFileTreeFactory.create(dir));
                    fileSystemMirror.putDirectory(snapshot);
                }
                return snapshot;
            }
        });
    }

    @Override
    public FileTreeSnapshot snapshotDirectoryTree(final DirectoryFileTree dirTree) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        // Currently cache only those trees where we want everything from a directory
        if (!dirTree.getPatterns().isEmpty()) {
            FileVisitorImpl visitor = new FileVisitorImpl();
            dirTree.visit(visitor);
            return new DirectoryTreeDetails(dirTree.getDir().getAbsolutePath(), visitor.getElements());
        }

        final String path = dirTree.getDir().getAbsolutePath();
        return producingTrees.guardByKey(path, new Factory<FileTreeSnapshot>() {
            @Override
            public FileTreeSnapshot create() {
                FileTreeSnapshot snapshot = fileSystemMirror.getDirectoryTree(path);
                if (snapshot == null) {
                    // Scan the directory
                    snapshot = doSnapshot(dirTree);
                    fileSystemMirror.putDirectory(snapshot);
                }
                return snapshot;
            }
        });
    }

    @Override
    public List<FileSnapshot> snapshotTree(FileTreeInternal tree) {
        FileVisitorImpl visitor = new FileVisitorImpl();
        tree.visitTreeOrBackingFile(visitor);
        return visitor.getElements();
    }

    private FileTreeSnapshot doSnapshot(DirectoryFileTree directoryTree) {
        String path = internPath(directoryTree.getDir());
        FileVisitorImpl visitor = new FileVisitorImpl();
        directoryTree.visit(visitor);
        return new DirectoryTreeDetails(path, ImmutableList.copyOf(visitor.getElements()));
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
    }

    private class FileVisitorImpl implements FileVisitor {
        private final static int BATCH_SIZE = 32;
        private final ArrayList<FileSnapshot> fileTreeElements;
        private final Runnable[] buffer;
        private int bufferSize;
        private boolean completed;

        FileVisitorImpl() {
            this.fileTreeElements = Lists.newArrayList();
            this.buffer = new Runnable[BATCH_SIZE];
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            fileTreeElements.add(new DirectoryFileSnapshot(internPath(dirDetails.getFile()), dirDetails.getRelativePath(), false));
        }

        @Override
        public void visitFile(final FileVisitDetails fileDetails) {
            if (buffer != null) {
                visitFileBuffered(fileDetails);
            } else {
                visitFileDirect(fileDetails);
            }
        }

        private void visitFileBuffered(final FileVisitDetails fileDetails) {
            final DeferredFileSnapshot deferred = new DeferredFileSnapshot(fileDetails);
            buffer[bufferSize++] = deferred;
            fileTreeElements.add(deferred);
            if (bufferSize == BATCH_SIZE) {
                flush();
            }
        }

        private void visitFileDirect(final FileVisitDetails fileDetails) {
            fileTreeElements.add(new RegularFileSnapshot(internPath(fileDetails.getFile()), fileDetails.getRelativePath(), false, fileSnapshot(fileDetails)));
        }

        public List<FileSnapshot> getElements() {
            if (completed) {
                return fileTreeElements;
            }
            flush();
            int i = 0;
            for (FileSnapshot element : fileTreeElements) {
                if (element instanceof DeferredFileSnapshot) {
                    fileTreeElements.set(i, ((DeferredFileSnapshot) element).getResult());
                }
                i++;
            }
            completed = true;
            return fileTreeElements;

        }

        private void flush() {
            if (buffer == null || bufferSize == 0) {
                return;
            }
            if (bufferSize == 1) {
                synchronousSnapshot();
                return;
            }
            submitForConcurrentExecution();
        }

        private void synchronousSnapshot() {
            buffer[0].run();
            buffer[0] = null;
            bufferSize = 0;
        }

        private void submitForConcurrentExecution() {
            final Runnable[] tasks = buffer.clone();
            final int len = bufferSize;
            for (int i = 0; i < bufferSize; i++) {
                buffer[i] = null;
            }
            bufferSize = 0;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < len; i++) {
                        tasks[i].run();
                    }
                }
            });
        }
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();
    }

    private class DeferredFileSnapshot implements FileSnapshot, Runnable {
        private final Object lock = new Object();
        private final FileVisitDetails details;
        private FileSnapshot delegate;

        private DeferredFileSnapshot(FileVisitDetails details) {
            this.details = details;
        }

        private FileSnapshot getResult() {
            while (delegate == null) {
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }
            return delegate;
        }

        private IllegalStateException shouldNotBeCalled() {
            return new IllegalStateException("Method called before the snapshot has been computed.");
        }

        @Override
        public String getPath() {
            throw shouldNotBeCalled();
        }

        @Override
        public String getName() {
            throw shouldNotBeCalled();
        }

        @Override
        public FileType getType() {
            throw shouldNotBeCalled();
        }

        @Override
        public boolean isRoot() {
            throw shouldNotBeCalled();
        }

        @Override
        public RelativePath getRelativePath() {
            throw shouldNotBeCalled();
        }

        @Override
        public FileContentSnapshot getContent() {
            throw shouldNotBeCalled();
        }

        @Override
        public FileSnapshot withContentHash(HashCode contentHash) {
            throw shouldNotBeCalled();
        }

        @Override
        public void run() {
            synchronized (lock) {
                try {
                    delegate = new RegularFileSnapshot(internPath(details.getFile()), details.getRelativePath(), false, fileSnapshot(details));
                } finally {
                    lock.notifyAll();
                }
            }
        }
    }
}
