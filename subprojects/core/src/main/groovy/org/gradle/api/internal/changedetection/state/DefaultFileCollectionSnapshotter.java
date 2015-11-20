/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.CachingFileVisitDetails;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.*;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;
import org.gradle.util.NoOpChangeListener;

import java.io.File;
import java.math.BigInteger;
import java.util.*;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileTreeElementSnapshotter snapshotter;
    private TaskArtifactStateCacheAccess cacheAccess;
    private final StringInterner stringInterner;

    public DefaultFileCollectionSnapshotter(FileTreeElementSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
    }

    public void registerSerializers(SerializerRegistry<FileCollectionSnapshot> registry) {
        registry.register(FileCollectionSnapshotImpl.class, new DefaultFileSnapshotterSerializer(stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(new HashMap<String, IncrementalFileSnapshot>());
    }

    public FileCollectionSnapshot snapshot(final FileCollection input) {
        final List<FileVisitDetails> allFileVisitDetails = visitFiles(input);

        if (allFileVisitDetails.isEmpty()) {
            return new FileCollectionSnapshotImpl(Collections.<String, IncrementalFileSnapshot>emptyMap());
        }

        final Map<String, IncrementalFileSnapshot> snapshots = new HashMap<String, IncrementalFileSnapshot>();

        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (FileVisitDetails fileDetails : allFileVisitDetails) {
                    final String absolutePath = stringInterner.intern(fileDetails.getFile().getAbsolutePath());
                    if (!snapshots.containsKey(absolutePath)) {
                        if (fileDetails.isDirectory()) {
                            snapshots.put(absolutePath, DirSnapshot.getInstance());
                        } else {
                            snapshots.put(absolutePath, new FileHashSnapshot(snapshotter.snapshot(fileDetails).getHash()));
                        }
                    }
                }
            }
        });

        return new FileCollectionSnapshotImpl(snapshots);
    }

    private List<FileVisitDetails> visitFiles(FileCollection input) {
        final List<FileVisitDetails> allFileVisitDetails = new LinkedList<FileVisitDetails>();

        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext();
        context.add(input);
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees();

        for (FileTreeInternal fileTree : fileTrees) {
            Set<File> fileTreeBackingFiles = unwrapFileTreeBackingFilesIfAvailable(fileTree);
            if (fileTreeBackingFiles != null) {
                for (File fileTreeSourceFile : fileTreeBackingFiles) {
                    allFileVisitDetails.add(new CachingFileVisitDetails(fileTreeSourceFile));
                }
            } else {
                fileTree.visit(new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                        allFileVisitDetails.add(dirDetails);
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails) {
                        allFileVisitDetails.add(fileDetails);
                    }
                });
            }
        }
        return allFileVisitDetails;
    }

    private static Set<File> unwrapFileTreeBackingFilesIfAvailable(Object fileTree) {
        if (fileTree instanceof FileTreeWithBackingFile) {
            File backingFile = ((FileTreeWithBackingFile) fileTree).getBackingFile();
            if (backingFile != null) {
                return Collections.singleton(backingFile);
            }
            if (fileTree instanceof FileSystemMirroringFileTree) {
                // custom resource as source for TarFileTree, fallback to snapshotting files in archive
                return new FileTreeAdapter((FileSystemMirroringFileTree) fileTree).getFiles();
            }
        } else if (fileTree instanceof FileTreeAdapter) {
            return unwrapFileTreeBackingFilesIfAvailable(((FileTreeAdapter) fileTree).getTree());
        } else if (fileTree instanceof FilteredFileTree) {
            return unwrapFileTreeBackingFilesIfAvailable(((FilteredFileTree) fileTree).getOriginalFileTree());
        }
        return null;
    }

    interface IncrementalFileSnapshot {
        boolean isUpToDate(IncrementalFileSnapshot snapshot);
    }

    static class FileHashSnapshot implements IncrementalFileSnapshot, FileSnapshot {
        final byte[] hash;

        public FileHashSnapshot(byte[] hash) {
            this.hash = hash;
        }

        public boolean isUpToDate(IncrementalFileSnapshot snapshot) {
            if (!(snapshot instanceof FileHashSnapshot)) {
                return false;
            }

            FileHashSnapshot other = (FileHashSnapshot) snapshot;
            return Arrays.equals(hash, other.hash);
        }

        @Override
        public String toString() {
            return new BigInteger(1, hash).toString(16);
        }

        public byte[] getHash() {
            return hash;
        }
    }

    static class DirSnapshot implements IncrementalFileSnapshot {
        private static DirSnapshot instance = new DirSnapshot();

        private DirSnapshot() {
        }

        static DirSnapshot getInstance() {
            return instance;
        }

        public boolean isUpToDate(IncrementalFileSnapshot snapshot) {
            return snapshot instanceof DirSnapshot;
        }
    }

    static class MissingFileSnapshot implements IncrementalFileSnapshot {
        public boolean isUpToDate(IncrementalFileSnapshot snapshot) {
            return snapshot instanceof MissingFileSnapshot;
        }
    }

    static class FileCollectionSnapshotImpl implements FileCollectionSnapshot {
        final Map<String, IncrementalFileSnapshot> snapshots;

        public FileCollectionSnapshotImpl(Map<String, IncrementalFileSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        public FileCollection getFiles() {
            List<File> files = new ArrayList<File>();
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
                if (entry.getValue() instanceof FileHashSnapshot) {
                    files.add(new File(entry.getKey()));
                }
            }
            return new SimpleFileCollection(files);
        }

        public FilesSnapshotSet getSnapshot() {
            return new FilesSnapshotSet() {
                public FileSnapshot findSnapshot(File file) {
                    IncrementalFileSnapshot s = snapshots.get(file.getAbsolutePath());
                    if (s instanceof FileSnapshot) {
                        return (FileSnapshot) s;
                    }
                    return null;
                }
            };
        }

        public ChangeIterator<String> iterateChangesSince(FileCollectionSnapshot oldSnapshot) {
            FileCollectionSnapshotImpl other = (FileCollectionSnapshotImpl) oldSnapshot;
            final Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(other.snapshots);
            final Iterator<String> currentFiles = snapshots.keySet().iterator();

            return new ChangeIterator<String>() {
                private Iterator<String> removedFiles;

                public boolean next(ChangeListener<String> listener) {
                    while (currentFiles.hasNext()) {
                        String currentFile = currentFiles.next();
                        IncrementalFileSnapshot otherFile = otherSnapshots.remove(currentFile);

                        if (otherFile == null) {
                            listener.added(currentFile);
                            return true;
                        } else if (!snapshots.get(currentFile).isUpToDate(otherFile)) {
                            listener.changed(currentFile);
                            return true;
                        }
                    }

                    // Create a single iterator to use for all of the removed files
                    if (removedFiles == null) {
                        removedFiles = otherSnapshots.keySet().iterator();
                    }

                    if (removedFiles.hasNext()) {
                        listener.removed(removedFiles.next());
                        return true;
                    }

                    return false;
                }
            };
        }

        public Diff changesSince(final FileCollectionSnapshot oldSnapshot) {
            final FileCollectionSnapshotImpl other = (FileCollectionSnapshotImpl) oldSnapshot;
            return new Diff() {
                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot) {
                    return applyTo(snapshot, new NoOpChangeListener<Merge>());
                }

                public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot, final ChangeListener<Merge> listener) {
                    FileCollectionSnapshotImpl target = (FileCollectionSnapshotImpl) snapshot;
                    final Map<String, IncrementalFileSnapshot> newSnapshots = new HashMap<String, IncrementalFileSnapshot>(target.snapshots);
                    diff(snapshots, other.snapshots, new MapMergeChangeListener<String, IncrementalFileSnapshot>(listener, newSnapshots));
                    return new FileCollectionSnapshotImpl(newSnapshots);
                }
            };
        }

        private void diff(Map<String, IncrementalFileSnapshot> snapshots, Map<String, IncrementalFileSnapshot> oldSnapshots,
                          ChangeListener<Map.Entry<String, IncrementalFileSnapshot>> listener) {
            Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(oldSnapshots);
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
                IncrementalFileSnapshot otherFile = otherSnapshots.remove(entry.getKey());
                if (otherFile == null) {
                    listener.added(entry);
                } else if (!entry.getValue().isUpToDate(otherFile)) {
                    listener.changed(entry);
                }
            }
            for (Map.Entry<String, IncrementalFileSnapshot> entry : otherSnapshots.entrySet()) {
                listener.removed(entry);
            }
        }

    }
}
