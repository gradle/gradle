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
import org.gradle.messaging.serialize.DefaultSerializerRegistry;
import org.gradle.messaging.serialize.LongSerializer;
import org.gradle.messaging.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;
import org.gradle.util.DiffUtil;
import org.gradle.util.NoOpChangeListener;

import java.io.File;
import java.util.*;

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
public class OutputFilesCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileCollectionSnapshotter snapshotter;
    private final IdGenerator<Long> idGenerator;
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final PersistentIndexedCache<String, Long> dirIdentifierCache;

    public OutputFilesCollectionSnapshotter(FileCollectionSnapshotter snapshotter, IdGenerator<Long> idGenerator,
                                            TaskArtifactStateCacheAccess cacheAccess) {
        this.snapshotter = snapshotter;
        this.idGenerator = idGenerator;
        this.cacheAccess = cacheAccess;
        dirIdentifierCache = cacheAccess.createCache("outputFileStates", String.class, new LongSerializer());
    }

    public void registerSerializers(SerializerRegistry<FileCollectionSnapshot> registry) {
        DefaultSerializerRegistry<FileCollectionSnapshot> nested = new DefaultSerializerRegistry<FileCollectionSnapshot>();
        snapshotter.registerSerializers(nested);
        registry.register(OutputFilesSnapshot.class, new OutputFilesSnapshotSerializer(nested.build()));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new OutputFilesSnapshot(new HashMap<String, Long>(), snapshotter.emptySnapshot());
    }

    public OutputFilesSnapshot snapshot(final FileCollection files) {
        final Map<String, Long> snapshotDirIds = new HashMap<String, Long>();
        final Set<File> theFiles = files.getFiles();
        cacheAccess.useCache("create dir snapshots", new Runnable() {
            public void run() {
                for (File file : theFiles) {
                    Long dirId;
                    if (file.exists()) {
                        dirId = dirIdentifierCache.get(file.getAbsolutePath());
                        if (dirId == null) {
                            dirId = idGenerator.generateId();
                            dirIdentifierCache.put(file.getAbsolutePath(), dirId);
                        }
                    } else {
                        dirIdentifierCache.remove(file.getAbsolutePath());
                        dirId = null;
                    }
                    snapshotDirIds.put(file.getAbsolutePath(), dirId);
                }

            }
        });
        return new OutputFilesSnapshot(snapshotDirIds, snapshotter.snapshot(files));
    }

    static class OutputFilesSnapshot implements FileCollectionSnapshot {
        final Map<String, Long> rootFileIds;
        final FileCollectionSnapshot filesSnapshot;

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

        public ChangeIterator<String> iterateChangesSince(FileCollectionSnapshot oldSnapshot) {
            final OutputFilesSnapshot other = (OutputFilesSnapshot) oldSnapshot;
            final ChangeIterator<String> rootFileIdIterator = iterateRootFileIdChanges(other);
            final ChangeIterator<String> fileIterator = filesSnapshot.iterateChangesSince(other.filesSnapshot);

            final AddIgnoreChangeListenerAdapter listenerAdapter = new AddIgnoreChangeListenerAdapter();
            return new ChangeIterator<String>() {
                public boolean next(final ChangeListener<String> listener) {
                    listenerAdapter.withDelegate(listener);
                    if (rootFileIdIterator.next(listener)) {
                        return true;
                    }

                    while (fileIterator.next(listenerAdapter)) {
                        if (!listenerAdapter.wasIgnored) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }

        private ChangeIterator<String> iterateRootFileIdChanges(final OutputFilesSnapshot other) {
            // Inlining DiffUtil.diff makes the inefficiencies here a bit more explicit
            Map<String, Long> added = new HashMap<String, Long>(rootFileIds);
            added.keySet().removeAll(other.rootFileIds.keySet());
            final Iterator<String> addedIterator = added.keySet().iterator();

            Map<String, Long> removed = new HashMap<String, Long>(other.rootFileIds);
            removed.keySet().removeAll(rootFileIds.keySet());
            final Iterator<String> removedIterator = removed.keySet().iterator();

            Set<String> changed = new HashSet<String>();
            for (Map.Entry<String, Long> current : rootFileIds.entrySet()) {
                 // Only care about rootIds that used to exist, and have changed or been removed
                Long otherValue = other.rootFileIds.get(current.getKey());
                if (otherValue != null && !otherValue.equals(current.getValue())) {
                    changed.add(current.getKey());
                }
            }
            final Iterator<String> changedIterator = changed.iterator();

            return new ChangeIterator<String>() {
                public boolean next(ChangeListener<String> listener) {
                    if (addedIterator.hasNext()) {
                        listener.added(addedIterator.next());
                        return true;
                    }
                    if (removedIterator.hasNext()) {
                        listener.removed(removedIterator.next());
                        return true;
                    }
                    if (changedIterator.hasNext()) {
                        listener.changed(changedIterator.next());
                        return true;
                    }

                    return false;
                }
            };
        }
    }

    /**
     * A flyweight wrapper that is used to ignore any added files called.
     */
    private static class AddIgnoreChangeListenerAdapter implements ChangeListener<String> {
        private ChangeListener<String> delegate;
        boolean wasIgnored;

        private void withDelegate(ChangeListener<String> delegate) {
            this.delegate = delegate;
        }

        public void added(String element) {
            wasIgnored = true;
        }

        public void removed(String element) {
            delegate.removed(element);
            wasIgnored = false;
        }

        public void changed(String element) {
            delegate.changed(element);
            wasIgnored = false;
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
