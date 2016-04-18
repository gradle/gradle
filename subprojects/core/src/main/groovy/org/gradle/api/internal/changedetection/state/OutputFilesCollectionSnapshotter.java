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
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.*;

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

    public OutputFilesSnapshot snapshot(FileCollection files, boolean allowReuse) {
        Map<String, Boolean> roots = new HashMap<String, Boolean>();
        for (File file : files.getFiles()) {
            roots.put(stringInterner.intern(file.getAbsolutePath()), file.exists());
        }
        return new OutputFilesSnapshot(roots, snapshotter.snapshot(files, allowReuse));
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

        public FilesSnapshotSet getSnapshot() {
            return filesSnapshot.getSnapshot();
        }

        @Override
        public FileCollectionSnapshot updateFrom(FileCollectionSnapshot newSnapshot) {
            OutputFilesSnapshot newOutputsSnapshot = (OutputFilesSnapshot) newSnapshot;
            return new OutputFilesSnapshot(roots, filesSnapshot.updateFrom(newOutputsSnapshot.filesSnapshot));
        }

        @Override
        public FileCollectionSnapshot applyAllChangesSince(FileCollectionSnapshot oldSnapshot, FileCollectionSnapshot target) {
            OutputFilesSnapshot oldOutputsSnapshot = (OutputFilesSnapshot) oldSnapshot;
            OutputFilesSnapshot targetOutputsSnapshot = (OutputFilesSnapshot) target;
            return new OutputFilesSnapshot(roots, filesSnapshot.applyAllChangesSince(oldOutputsSnapshot.filesSnapshot, targetOutputsSnapshot.filesSnapshot));
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
}
