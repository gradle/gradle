/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.math.BigInteger;
import java.util.*;

abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    protected final FileSnapshotter snapshotter;
    protected final StringInterner stringInterner;
    protected final FileResolver fileResolver;
    protected TaskArtifactStateCacheAccess cacheAccess;

    public AbstractFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
        this.fileResolver = fileResolver;
    }

    public void registerSerializers(SerializerRegistry<FileCollectionSnapshot> registry) {
        registry.register(FileCollectionSnapshotImpl.class, new DefaultFileSnapshotterSerializer(stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(Collections.<String, IncrementalFileSnapshot>emptyMap());
    }

    public FileCollectionSnapshot snapshot(final FileCollection input) {
        final List<FileVisitDetails> allFileVisitDetails = Lists.newLinkedList();
        final List<File> missingFiles = Lists.newArrayList();

        visitFiles(input, allFileVisitDetails, missingFiles);

        if (allFileVisitDetails.isEmpty() && missingFiles.isEmpty()) {
            return new FileCollectionSnapshotImpl(Collections.<String, IncrementalFileSnapshot>emptyMap());
        }

        final Map<String, IncrementalFileSnapshot> snapshots = new HashMap<String, IncrementalFileSnapshot>();

        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (FileVisitDetails fileDetails : allFileVisitDetails) {
                    String absolutePath = stringInterner.intern(fileDetails.getFile().getAbsolutePath());
                    if (!snapshots.containsKey(absolutePath)) {
                        if (fileDetails.isDirectory()) {
                            snapshots.put(absolutePath, DirSnapshot.getInstance());
                        } else {
                            snapshots.put(absolutePath, new FileHashSnapshot(snapshotter.snapshot(fileDetails).getHash(), fileDetails.getLastModified()));
                        }
                    }
                }
                for (File missingFile : missingFiles) {
                    String absolutePath = stringInterner.intern(missingFile.getAbsolutePath());
                    if (!snapshots.containsKey(absolutePath)) {
                        snapshots.put(absolutePath, MissingFileSnapshot.getInstance());
                    }
                }
            }
        });

        return new FileCollectionSnapshotImpl(snapshots);
    }

    abstract protected void visitFiles(FileCollection input, List<FileVisitDetails> allFileVisitDetails, List<File> missingFiles);

    interface IncrementalFileSnapshot {
        boolean isContentUpToDate(IncrementalFileSnapshot snapshot);

        boolean isContentAndMetadataUpToDate(IncrementalFileSnapshot snapshot);
    }

    static class FileHashSnapshot implements IncrementalFileSnapshot, FileSnapshot {
        final byte[] hash;
        final transient long lastModified; // Currently not persisted

        public FileHashSnapshot(byte[] hash) {
            this.hash = hash;
            this.lastModified = 0;
        }

        public FileHashSnapshot(byte[] hash, long lastModified) {
            this.hash = hash;
            this.lastModified = lastModified;
        }

        public boolean isContentUpToDate(IncrementalFileSnapshot snapshot) {
            if (!(snapshot instanceof FileHashSnapshot)) {
                return false;
            }
            FileHashSnapshot other = (FileHashSnapshot) snapshot;
            return Arrays.equals(hash, other.hash);
        }

        @Override
        public boolean isContentAndMetadataUpToDate(IncrementalFileSnapshot snapshot) {
            if (!(snapshot instanceof FileHashSnapshot)) {
                return false;
            }
            FileHashSnapshot other = (FileHashSnapshot) snapshot;
            return lastModified == other.lastModified && Arrays.equals(hash, other.hash);
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

        @Override
        public boolean isContentAndMetadataUpToDate(IncrementalFileSnapshot snapshot) {
            return isContentUpToDate(snapshot);
        }

        public boolean isContentUpToDate(IncrementalFileSnapshot snapshot) {
            return snapshot instanceof DirSnapshot;
        }
    }

    static class MissingFileSnapshot implements IncrementalFileSnapshot {
        private static MissingFileSnapshot instance = new MissingFileSnapshot();

        private MissingFileSnapshot() {
        }

        static MissingFileSnapshot getInstance() {
            return instance;
        }

        @Override
        public boolean isContentAndMetadataUpToDate(IncrementalFileSnapshot snapshot) {
            return isContentUpToDate(snapshot);
        }

        public boolean isContentUpToDate(IncrementalFileSnapshot snapshot) {
            return snapshot instanceof MissingFileSnapshot;
        }
    }

    static class FileCollectionSnapshotImpl implements FileCollectionSnapshot {
        final Map<String, IncrementalFileSnapshot> snapshots;

        public FileCollectionSnapshotImpl(Map<String, IncrementalFileSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        public List<File> getFiles() {
            List<File> files = Lists.newArrayList();
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
                if (!(entry.getValue() instanceof DirSnapshot)) {
                    files.add(new File(entry.getKey()));
                }
            }
            return files;
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

        @Override
        public ChangeIterator<String> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, final Set<ChangeFilter> filters) {
            FileCollectionSnapshotImpl oldSnapshotImpl = (FileCollectionSnapshotImpl) oldSnapshot;
            final Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(oldSnapshotImpl.snapshots);
            final Iterator<String> currentFiles = snapshots.keySet().iterator();
            final boolean includeAdded = !filters.contains(ChangeFilter.IgnoreAddedFiles);

            return new ChangeIterator<String>() {
                private Iterator<String> removedFiles;

                public boolean next(ChangeListener<String> listener) {
                    while (currentFiles.hasNext()) {
                        String currentFile = currentFiles.next();
                        IncrementalFileSnapshot otherFile = otherSnapshots.remove(currentFile);
                        if (otherFile == null) {
                            if (includeAdded) {
                                listener.added(currentFile);
                                return true;
                            }
                        } else if (!snapshots.get(currentFile).isContentUpToDate(otherFile)) {
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

        @Override
        public FileCollectionSnapshot updateFrom(FileCollectionSnapshot newSnapshot) {
            if (snapshots.isEmpty()) {
                // Nothing to update
                return this;
            }
            FileCollectionSnapshotImpl newSnapshotImpl = (FileCollectionSnapshotImpl) newSnapshot;
            if (newSnapshotImpl.snapshots.isEmpty()) {
                // Everything has been removed
                return newSnapshotImpl;
            }

            // Update entries from new snapshot
            Map<String, IncrementalFileSnapshot> newSnapshots = new HashMap<String, IncrementalFileSnapshot>(snapshots.size());
            for (String path : snapshots.keySet()) {
                IncrementalFileSnapshot newValue = newSnapshotImpl.snapshots.get(path);
                if (newValue != null) {
                    newSnapshots.put(path, newValue);
                }
            }
            return new FileCollectionSnapshotImpl(newSnapshots);
        }

        @Override
        public FileCollectionSnapshot applyAllChangesSince(FileCollectionSnapshot oldSnapshot, FileCollectionSnapshot target) {
            FileCollectionSnapshotImpl oldSnapshotImpl = (FileCollectionSnapshotImpl) oldSnapshot;
            FileCollectionSnapshotImpl targetImpl = (FileCollectionSnapshotImpl) target;
            Map<String, IncrementalFileSnapshot> newSnapshots = new HashMap<String, IncrementalFileSnapshot>(targetImpl.snapshots);
            diff(snapshots, oldSnapshotImpl.snapshots, newSnapshots);
            return new FileCollectionSnapshotImpl(newSnapshots);
        }

        private void diff(Map<String, IncrementalFileSnapshot> snapshots, Map<String, IncrementalFileSnapshot> oldSnapshots, Map<String, IncrementalFileSnapshot> target) {
            if (oldSnapshots.isEmpty()) {
                // Everything is new
                target.putAll(snapshots);
                return;
            }

            Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(oldSnapshots);
            for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
                IncrementalFileSnapshot otherFile = otherSnapshots.remove(entry.getKey());
                if (otherFile == null) {
                    target.put(entry.getKey(), entry.getValue());
                } else if (!entry.getValue().isContentAndMetadataUpToDate(otherFile)) {
                    target.put(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<String, IncrementalFileSnapshot> entry : otherSnapshots.entrySet()) {
                target.remove(entry.getKey());
            }
        }

    }
}
