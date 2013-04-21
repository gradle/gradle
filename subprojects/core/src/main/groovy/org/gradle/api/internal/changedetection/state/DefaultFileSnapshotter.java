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
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.util.ChangeListener;
import org.gradle.util.NoOpChangeListener;

import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;

public class DefaultFileSnapshotter implements FileSnapshotter {
    private final Hasher hasher;

    public DefaultFileSnapshotter(Hasher hasher) {
        this.hasher = hasher;
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(new HashMap<String, FileSnapshot>());
    }

    public FileCollectionSnapshot snapshot(FileCollection sourceFiles) {
        Map<String, FileSnapshot> snapshots = new HashMap<String, FileSnapshot>();
        for (File file : sourceFiles.getAsFileTree()) {
            if (file.isFile()) {
                snapshots.put(file.getAbsolutePath(), new FileHashSnapshot(hasher.hash(file)));
            } else if (file.isDirectory()) {
                snapshots.put(file.getAbsolutePath(), new DirSnapshot());
            } else {
                snapshots.put(file.getAbsolutePath(), new MissingFileSnapshot());
            }
        }
        return new FileCollectionSnapshotImpl(snapshots);
    }

    private interface FileSnapshot extends Serializable {
        boolean isUpToDate(FileSnapshot snapshot);
    }

    private static class FileHashSnapshot implements FileSnapshot {
        private final byte[] hash;

        public FileHashSnapshot(byte[] hash) {
            this.hash = hash;
        }

        public boolean isUpToDate(FileSnapshot snapshot) {
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
    }

    private static class DirSnapshot implements FileSnapshot {
        public boolean isUpToDate(FileSnapshot snapshot) {
            return snapshot instanceof DirSnapshot;
        }
    }

    private static class MissingFileSnapshot implements FileSnapshot {
        public boolean isUpToDate(FileSnapshot snapshot) {
            return snapshot instanceof MissingFileSnapshot;
        }
    }

    private static class FileCollectionSnapshotImpl implements FileCollectionSnapshot {
        private final Map<String, FileSnapshot> snapshots;

        public FileCollectionSnapshotImpl(Map<String, FileSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        public FileCollection getFiles() {
            List<File> files = new ArrayList<File>();
            for (Map.Entry<String, FileSnapshot> entry : snapshots.entrySet()) {
                if (entry.getValue() instanceof FileHashSnapshot) {
                    files.add(new File(entry.getKey()));
                }
            }
            return new SimpleFileCollection(files);
        }

        public ChangeIterator<String> iterateChangesSince(FileCollectionSnapshot oldSnapshot) {
            FileCollectionSnapshotImpl other = (FileCollectionSnapshotImpl) oldSnapshot;
            final Map<String, FileSnapshot> otherSnapshots = new HashMap<String, FileSnapshot>(other.snapshots);
            final Iterator<String> currentFiles = snapshots.keySet().iterator();

            return new ChangeIterator<String>() {
                private Iterator<String> removedFiles;

                public boolean next(ChangeListener<String> listener) {
                    while (currentFiles.hasNext()) {
                        String currentFile = currentFiles.next();
                        FileSnapshot otherFile = otherSnapshots.remove(currentFile);

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
                    final Map<String, FileSnapshot> newSnapshots = new HashMap<String, FileSnapshot>(target.snapshots);
                    diff(snapshots, other.snapshots, new MapMergeChangeListener<String, FileSnapshot>(listener, newSnapshots));
                    return new FileCollectionSnapshotImpl(newSnapshots);
                }
            };
        }

        private void diff(Map<String, FileSnapshot> snapshots, Map<String, FileSnapshot> oldSnapshots,
                          ChangeListener<Map.Entry<String, FileSnapshot>> listener) {
            Map<String, FileSnapshot> otherSnapshots = new HashMap<String, FileSnapshot>(oldSnapshots);
            for (Map.Entry<String, FileSnapshot> entry : snapshots.entrySet()) {
                FileSnapshot otherFile = otherSnapshots.remove(entry.getKey());
                if (otherFile == null) {
                    listener.added(entry);
                } else if (!entry.getValue().isUpToDate(otherFile)) {
                    listener.changed(entry);
                }
            }
            for (Map.Entry<String, FileSnapshot> entry : otherSnapshots.entrySet()) {
                listener.removed(entry);
            }
        }

    }
}
