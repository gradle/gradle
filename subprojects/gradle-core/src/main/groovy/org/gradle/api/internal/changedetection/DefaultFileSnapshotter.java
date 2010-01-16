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
package org.gradle.api.internal.changedetection;

import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DefaultFileSnapshotter implements FileSnapshotter {
    private final Hasher hasher;

    public DefaultFileSnapshotter(Hasher hasher) {
        this.hasher = hasher;
    }

    public FileCollectionSnapshot snapshot(FileCollection sourceFiles) {
        Map<String, FileSnapshot> snapshots = new HashMap<String, FileSnapshot>();
        for (File file : sourceFiles) {
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

        public void changesSince(FileCollectionSnapshot snapshot, ChangeListener listener) {
            FileCollectionSnapshotImpl other = (FileCollectionSnapshotImpl) snapshot;
            Map<String, FileSnapshot> otherSnapshots = new HashMap<String, FileSnapshot>(other.snapshots);
            for (Map.Entry<String, FileSnapshot> entry : snapshots.entrySet()) {
                FileSnapshot otherFile = otherSnapshots.remove(entry.getKey());
                if (otherFile == null) {
                    listener.added(new File(entry.getKey()));
                } else if (!entry.getValue().isUpToDate(otherFile)) {
                    listener.changed(new File(entry.getKey()));
                }
            }
            for (String file : otherSnapshots.keySet()) {
                listener.removed(new File(file));
            }
        }
    }
}
