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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
    public FileCollectionSnapshot snapshot(FileCollection files, TaskFilePropertyCompareType compareType) {
        return new OutputFilesSnapshot(getRoots(files), snapshotter.snapshot(files, compareType));
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
        Map<String, IncrementalFileSnapshot> afterSnapshots = afterExecution.getSnapshots();
        if (!beforeExecution.getSnapshots().isEmpty() && !afterSnapshots.isEmpty()) {
            Map<String, IncrementalFileSnapshot> beforeSnapshots = beforeExecution.getSnapshots();
            Map<String, IncrementalFileSnapshot> previousSnapshots = afterPreviousExecution != null ? afterPreviousExecution.getSnapshots() : new HashMap<String, IncrementalFileSnapshot>();
            int newEntryCount = 0;
            ImmutableMap.Builder<String, IncrementalFileSnapshot> newEntries = ImmutableMap.builder();

            for (Map.Entry<String, IncrementalFileSnapshot> entry : afterSnapshots.entrySet()) {
                final String path = entry.getKey();
                IncrementalFileSnapshot otherFile = beforeSnapshots.get(path);
                if (otherFile == null
                    || !entry.getValue().isContentAndMetadataUpToDate(otherFile)
                    || previousSnapshots.containsKey(path)) {
                    newEntries.put(entry.getKey(), entry.getValue());
                    newEntryCount++;
                }
            }
            if (newEntryCount == afterSnapshots.size()) {
                filesSnapshot = afterExecution;
            } else {
                filesSnapshot = new FileCollectionSnapshotImpl(newEntries.build(), TaskFilePropertyCompareType.OUTPUT);
            }
        } else {
            filesSnapshot = afterExecution;
        }
        if (filesSnapshot instanceof OutputFilesSnapshot) {
            filesSnapshot = ((OutputFilesSnapshot) filesSnapshot).filesSnapshot;
        }
        return new OutputFilesSnapshot(getRoots(roots), filesSnapshot);
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

        @Override
        public boolean isEmpty() {
            return filesSnapshot.isEmpty();
        }

        @Override
        public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String fileType) {
            final OutputFilesSnapshot other = (OutputFilesSnapshot) oldSnapshot;
            final Iterator<TaskStateChange> rootFileIdIterator = iterateRootFileIdChanges(other, fileType);
            final Iterator<TaskStateChange> fileIterator = filesSnapshot.iterateContentChangesSince(other.filesSnapshot, fileType);
            return Iterators.concat(rootFileIdIterator, fileIterator);
        }

        private Iterator<TaskStateChange> iterateRootFileIdChanges(final OutputFilesSnapshot other, final String fileType) {
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
                if (otherValue != null && otherValue && !otherValue.equals(current.getValue())) {
                    changed.add(current.getKey());
                }
            }
            final Iterator<String> changedIterator = changed.iterator();

            return new AbstractIterator<TaskStateChange>() {
                @Override
                protected TaskStateChange computeNext() {
                    if (addedIterator.hasNext()) {
                        return new FileChange(addedIterator.next(), ChangeType.ADDED, fileType);
                    }
                    if (removedIterator.hasNext()) {
                        return new FileChange(removedIterator.next(), ChangeType.REMOVED, fileType);
                    }
                    if (changedIterator.hasNext()) {
                        return new FileChange(changedIterator.next(), ChangeType.MODIFIED, fileType);
                    }

                    return endOfData();
                }
            };
        }

        @Override
        public void appendToCacheKey(TaskCacheKeyBuilder builder) {
            filesSnapshot.appendToCacheKey(builder);
        }
    }
}
