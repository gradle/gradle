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
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.id.IdGenerator;
import org.gradle.util.ChangeListener;
import org.gradle.util.DiffUtil;
import org.gradle.util.NoOpChangeListener;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes a snapshot of the output files of a task. 2 parts to the algorithm:
 *
 * <ul>
 * <li>Collect the unique id for each output file and directory. The unique id is generated when we notice that
 * a file/directory has been created. The id is regenerated when the file/directory is deleted.</li>
 *
 * <li>Collect the hash of each output file and each file in each output directory.</li>
 * </ul>
 *
 */
public class OutputFilesSnapshotter implements FileSnapshotter {
    private final FileSnapshotter snapshotter;
    private final IdGenerator<Long> idGenerator;
    private final PersistentIndexedCache<String, Long> dirIdentiferCache;

    public OutputFilesSnapshotter(FileSnapshotter snapshotter, IdGenerator<Long> idGenerator,
                                  TaskArtifactStateCacheAccess cacheAccess) {
        this.snapshotter = snapshotter;
        this.idGenerator = idGenerator;
        dirIdentiferCache = cacheAccess.createCache("outputFileStates", String.class, Long.class);
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new OutputFilesSnapshot(new HashMap<String, Long>(), snapshotter.emptySnapshot());
    }

    public FileCollectionSnapshot snapshot(FileCollection files) {
        Map<String, Long> snapshotDirIds = new HashMap<String, Long>();
        for (File file : files) {
            Long dirId;
            if (file.exists()) {
                dirId = dirIdentiferCache.get(file.getAbsolutePath());
                if (dirId == null) {
                    dirId = idGenerator.generateId();
                    dirIdentiferCache.put(file.getAbsolutePath(), dirId);
                }
            } else {
                dirIdentiferCache.remove(file.getAbsolutePath());
                dirId = null;
            }
            snapshotDirIds.put(file.getAbsolutePath(), dirId);
        }
        return new OutputFilesSnapshot(snapshotDirIds, snapshotter.snapshot(files));
    }

    private static class OutputFilesSnapshot implements FileCollectionSnapshot {
        private final Map<String, Long> rootFileIds;
        private final FileCollectionSnapshot filesSnapshot;

        public OutputFilesSnapshot(Map<String, Long> rootFileIds, FileCollectionSnapshot filesSnapshot) {
            this.rootFileIds = rootFileIds;
            this.filesSnapshot = filesSnapshot;
        }

        public FileCollection getFiles() {
            return filesSnapshot.getFiles();
        }

        public Diff changesSince(final FileCollectionSnapshot oldSnapshot) {
            OutputFilesSnapshot other = (OutputFilesSnapshot) oldSnapshot;
            return new OutputFilesDiff(rootFileIds, other.rootFileIds, filesSnapshot.changesSince(other.filesSnapshot));
        }

        public void changesSince(FileCollectionSnapshot oldSnapshot, final SnapshotChangeListener listener) {
            assert listener.getResumeAfter() == null : "Output files do not support resuming";

            // TODO:DAZ Does not handle stop signal from controller in this part.
            final OutputFilesSnapshot other = (OutputFilesSnapshot) oldSnapshot;
            DiffUtil.diff(rootFileIds, other.rootFileIds, new ChangeListener<Map.Entry<String, Long>>() {
                public void added(Map.Entry<String, Long> element) {
                    listener.added(element.getKey());
                }

                public void removed(Map.Entry<String, Long> element) {
                    listener.removed(element.getKey());
                }

                public void changed(Map.Entry<String, Long> element) {
                    if (other.rootFileIds.get(element.getKey()) == null) {
                        // Dir used to not exist, now does. Don't care
                        return;
                    }
                    listener.changed(element.getKey());
                }
            });
            filesSnapshot.changesSince(other.filesSnapshot,
                    new SnapshotChangeListener() {
                        public void added(String fileName) {
                            // Ignore files added to output dirs which have been added since last time task executed
                        }

                        public void removed(String fileName) {
                            listener.removed(fileName);
                        }

                        public void changed(String fileName) {
                            listener.changed(fileName);
                        }

                        public String getResumeAfter() {
                            return null;
                        }

                        public boolean isStopped() {
                            return listener.isStopped();
                        }
                    }
            );
        }
    }

    private static class OutputFilesDiff implements FileCollectionSnapshot.Diff {
        private final Map<String, Long> newFileIds;
        private final Map<String, Long> oldFileIds;
        private final FileCollectionSnapshot.Diff filesDiff;

        public OutputFilesDiff(Map<String, Long> newFileIds, Map<String, Long> oldFileIds,
                               FileCollectionSnapshot.Diff filesDiff) {
            this.newFileIds = newFileIds;
            this.oldFileIds = oldFileIds;
            this.filesDiff = filesDiff;
        }

        public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot,
                                              ChangeListener<FileCollectionSnapshot.Merge> listener) {
            OutputFilesSnapshot other = (OutputFilesSnapshot) snapshot;
            Map<String, Long> dirIds = new HashMap<String, Long>(other.rootFileIds);
            DiffUtil.diff(newFileIds, oldFileIds, new MapMergeChangeListener<String, Long>(
                    new NoOpChangeListener<FileCollectionSnapshot.Merge>(), dirIds));
            return new OutputFilesSnapshot(newFileIds, filesDiff.applyTo(other.filesSnapshot, listener));
        }

        public FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot) {
            return applyTo(snapshot, new NoOpChangeListener<FileCollectionSnapshot.Merge>());
        }
    }
}
