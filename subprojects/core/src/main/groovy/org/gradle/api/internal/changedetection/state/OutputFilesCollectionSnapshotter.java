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

    public void registerSerializers(SerializerRegistry<FileCollectionSnapshot> registry) {
        DefaultSerializerRegistry<FileCollectionSnapshot> nested = new DefaultSerializerRegistry<FileCollectionSnapshot>();
        snapshotter.registerSerializers(nested);
        registry.register(OutputFilesSnapshot.class, new OutputFilesSnapshotSerializer(nested.build(), stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new OutputFilesSnapshot(Collections.<String>emptySet(), snapshotter.emptySnapshot());
    }

    public OutputFilesSnapshot snapshot(FileCollection files) {
        Set<String> roots = new LinkedHashSet<String>();
        for (File file : files.getFiles()) {
            roots.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return new OutputFilesSnapshot(roots, snapshotter.snapshot(files));
    }

    static class OutputFilesSnapshot implements FileCollectionSnapshot {
        final Set<String> roots;
        final FileCollectionSnapshot filesSnapshot;

        public OutputFilesSnapshot(Set<String> roots, FileCollectionSnapshot filesSnapshot) {
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

        public ChangeIterator<String> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot) {
            final OutputFilesSnapshot other = (OutputFilesSnapshot) oldSnapshot;
            final ChangeIterator<String> rootFileIdIterator = iterateRootFileIdChanges(other);
            final ChangeIterator<String> fileIterator = filesSnapshot.iterateContentChangesSince(other.filesSnapshot);

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
            Set<String> added = new LinkedHashSet<String>(roots);
            added.removeAll(other.roots);
            final Iterator<String> addedIterator = added.iterator();

            Set<String> removed = new LinkedHashSet<String>(other.roots);
            removed.removeAll(roots);
            final Iterator<String> removedIterator = removed.iterator();

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
}
