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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.BufferedStreamingHasher;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Takes a snapshot of the output files of a task.
 */
public class OutputFilesCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileCollectionSnapshotter snapshotter;
    private final StringInterner stringInterner;

    public OutputFilesCollectionSnapshotter(FileCollectionSnapshotter snapshotter, StringInterner stringInterner) {
        this.snapshotter = snapshotter;
        this.stringInterner = stringInterner;
    }

    public void registerSerializers(SerializerRegistry registry) {
        DefaultSerializerRegistry nested = new DefaultSerializerRegistry();
        snapshotter.registerSerializers(nested);
        registry.register(OutputFilesSnapshot.class, new OutputFilesSnapshotSerializer(nested.build(FileCollectionSnapshot.class), stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new OutputFilesSnapshot(Collections.<String, Boolean>emptyMap(), snapshotter.emptySnapshot());
    }

    @Override
    public FileCollectionSnapshot.PreCheck preCheck(FileCollection files, boolean allowReuse) {
        return new OutputFileCollectionSnapshotPreCheck(getRoots(files), snapshotter.preCheck(files, allowReuse));
    }

    private Map<String, Boolean> getRoots(FileCollection files) {
        Map<String, Boolean> roots = new HashMap<String, Boolean>();
        for (File file : files.getFiles()) {
            roots.put(stringInterner.intern(file.getAbsolutePath()), file.exists());
        }
        return roots;
    }

    /**
     * Returns a new snapshot that ignores new files between 2 previous snapshots
     */
    public OutputFilesSnapshot createOutputSnapshot(FileCollectionSnapshot afterPreviousExecution, FileCollectionSnapshot beforeExecution, FileCollectionSnapshot afterExecution, FileCollection roots) {
        FileCollectionSnapshot filesSnapshot;
        if (!beforeExecution.getSnapshots().isEmpty() && !afterExecution.getSnapshots().isEmpty()) {
            Map<String, IncrementalFileSnapshot> beforeSnapshots = beforeExecution.getSnapshots();
            Map<String, IncrementalFileSnapshot> previousSnapshots = afterPreviousExecution != null ? afterPreviousExecution.getSnapshots() : new HashMap<String, IncrementalFileSnapshot>();
            List<Map.Entry<String, IncrementalFileSnapshot>> newEntries = new ArrayList<Map.Entry<String, IncrementalFileSnapshot>>(afterExecution.getSnapshots().size());

            for (Map.Entry<String, IncrementalFileSnapshot> entry : afterExecution.getSnapshots().entrySet()) {
                final String path = entry.getKey();
                IncrementalFileSnapshot otherFile = beforeSnapshots.get(path);
                if (otherFile == null
                    || !entry.getValue().isContentAndMetadataUpToDate(otherFile)
                    || previousSnapshots.containsKey(path)) {
                    newEntries.add(entry);
                }
            }
            if (newEntries.size() == afterExecution.getSnapshots().size()) {
                filesSnapshot = afterExecution;
            } else {
                Map<String, IncrementalFileSnapshot> newSnapshots = new HashMap<String, IncrementalFileSnapshot>(newEntries.size());
                for (Map.Entry<String, IncrementalFileSnapshot> entry : newEntries) {
                    newSnapshots.put(entry.getKey(), entry.getValue());
                }
                filesSnapshot = new FileCollectionSnapshotImpl(newSnapshots);
            }
        } else {
            filesSnapshot = afterExecution;
        }
        if (filesSnapshot instanceof OutputFilesSnapshot) {
            filesSnapshot = ((OutputFilesSnapshot) filesSnapshot).filesSnapshot;
        }
        return new OutputFilesSnapshot(getRoots(roots), filesSnapshot);
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollectionSnapshot.PreCheck preCheck) {
        return new OutputFilesSnapshot(getRoots(preCheck.getFiles()), snapshotter.snapshot(preCheck));
    }

    static class OutputFilesSnapshot implements FileCollectionSnapshot {
        final Map<String, Boolean> roots;
        final FileCollectionSnapshot filesSnapshot;

        public OutputFilesSnapshot(Map<String, Boolean> roots, FileCollectionSnapshot filesSnapshot) {
            this.roots = roots;
            this.filesSnapshot = filesSnapshot;
        }

        public Collection<File> getFiles() {
            return filesSnapshot.getFiles();
        }

        @Override
        public Map<String, IncrementalFileSnapshot> getSnapshots() {
            return filesSnapshot.getSnapshots();
        }

        public FilesSnapshotSet getSnapshot() {
            return filesSnapshot.getSnapshot();
        }

        @Override
        public Collection<Long> getTreeSnapshotIds() {
            return filesSnapshot.getTreeSnapshotIds();
        }

        @Override
        public boolean isEmpty() {
            return filesSnapshot.isEmpty();
        }

        @Override
        public ChangeIterator<String> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, Set<ChangeFilter> filters) {
            final OutputFilesSnapshot other = (OutputFilesSnapshot) oldSnapshot;
            final ChangeIterator<String> rootFileIdIterator = iterateRootFileIdChanges(other);
            final ChangeIterator<String> fileIterator = filesSnapshot.iterateContentChangesSince(other.filesSnapshot, filters);

            return new ChangeIterator<String>() {
                public boolean next(final ChangeListener<String> listener) {
                    if (rootFileIdIterator.next(listener)) {
                        return true;
                    }
                    if (fileIterator.next(listener)) {
                        return true;
                    }
                    return false;
                }
            };
        }

        private ChangeIterator<String> iterateRootFileIdChanges(final OutputFilesSnapshot other) {
            Map<String, Boolean> added = new HashMap<String, Boolean>(roots);
            added.keySet().removeAll(other.roots.keySet());
            final Iterator<String> addedIterator = added.keySet().iterator();

            Map<String, Boolean> removed = new HashMap<String, Boolean>(other.roots);
            removed.keySet().removeAll(roots.keySet());
            final Iterator<String> removedIterator = removed.keySet().iterator();

            Set<String> changed = new HashSet<String>();
            for (Map.Entry<String, Boolean> current : roots.entrySet()) {
                Boolean otherValue = other.roots.get(current.getKey());
                // Only care about roots that used to exist and have been removed
                if (otherValue != null && otherValue.booleanValue() && !otherValue.equals(current.getValue())) {
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

    private static class OutputFileCollectionSnapshotPreCheck implements FileCollectionSnapshot.PreCheck {
        private final Map<String, Boolean> roots;
        private final FileCollectionSnapshot.PreCheck delegate;
        private Integer hash;

        public OutputFileCollectionSnapshotPreCheck(Map<String, Boolean> roots, FileCollectionSnapshot.PreCheck preCheck) {
            this.roots = roots;
            this.delegate = preCheck;
        }

        @Override
        public Integer getHash() {
            if (hash == null) {
                hash = calculatePreCheckHash();
            }
            return hash;
        }

        private Integer calculatePreCheckHash() {
            BufferedStreamingHasher hasher = new BufferedStreamingHasher();
            Encoder encoder = hasher.getEncoder();
            try {
                encoder.writeInt(delegate.getHash());
                SortedMap<String, Boolean> sortedRoots = new TreeMap<String, Boolean>(roots);
                for (Map.Entry<String, Boolean> entry : sortedRoots.entrySet()) {
                    encoder.writeString(entry.getKey());
                    encoder.writeBoolean(entry.getValue());
                }
                return hasher.checksum();
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public FileCollection getFiles() {
            return delegate.getFiles();
        }

        @Override
        public Collection<VisitedTree> getVisitedTrees() {
            return delegate.getVisitedTrees();
        }

        @Override
        public Collection<File> getMissingFiles() {
            return delegate.getMissingFiles();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }
    }
}
