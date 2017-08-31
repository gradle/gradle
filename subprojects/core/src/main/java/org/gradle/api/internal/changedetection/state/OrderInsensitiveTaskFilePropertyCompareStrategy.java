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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

class OrderInsensitiveTaskFilePropertyCompareStrategy implements TaskFilePropertyCompareStrategy.Impl {

    private static final Comparator<Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>> ENTRY_COMPARATOR = new Comparator<Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>>() {
        @Override
        public int compare(Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> o1, Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String fileType, boolean pathIsAbsolute, boolean includeAdded) {
        if (pathIsAbsolute) {
            return iterateChangesForAbsolutePaths(current, previous, fileType, includeAdded);
        } else {
            return iterateChangesForRelativePaths(current, previous, fileType, includeAdded);
        }
    }

    /**
     * A more efficient implementation when absolute paths are used.
     */
    private Iterator<TaskStateChange> iterateChangesForAbsolutePaths(final Map<String, NormalizedFileSnapshot> current, final Map<String, NormalizedFileSnapshot> previous, final String fileType, final boolean includeAdded) {
        final Set<String> unaccountedForPreviousSnapshots = new LinkedHashSet<String>(previous.keySet());
        final Iterator<Entry<String, NormalizedFileSnapshot>> currentEntries = current.entrySet().iterator();
        final List<String> added = new ArrayList<String>();
        return new AbstractIterator<TaskStateChange>() {
            private Iterator<String> unaccountedForPreviousSnapshotsIterator;
            private Iterator<String> addedIterator;

            @Override
            protected TaskStateChange computeNext() {
                while (currentEntries.hasNext()) {
                    Entry<String, NormalizedFileSnapshot> currentEntry = currentEntries.next();
                    String currentAbsolutePath = currentEntry.getKey();
                    NormalizedFileSnapshot currentNormalizedSnapshot = currentEntry.getValue();
                    FileContentSnapshot currentSnapshot = currentNormalizedSnapshot.getSnapshot();
                    if (unaccountedForPreviousSnapshots.remove(currentAbsolutePath)) {
                        NormalizedFileSnapshot previousNormalizedSnapshot = previous.get(currentAbsolutePath);
                        FileContentSnapshot previousSnapshot = previousNormalizedSnapshot.getSnapshot();
                        if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                            return FileChange.modified(currentAbsolutePath, fileType, previousSnapshot.getType(), currentSnapshot.getType());
                        }
                        // else, unchanged; check next file
                    } else {
                        added.add(currentAbsolutePath);
                    }
                }

                if (unaccountedForPreviousSnapshotsIterator == null) {
                    unaccountedForPreviousSnapshotsIterator = unaccountedForPreviousSnapshots.iterator();
                }
                if (unaccountedForPreviousSnapshotsIterator.hasNext()) {
                    String previousAbsolutePath = unaccountedForPreviousSnapshotsIterator.next();
                    return FileChange.removed(previousAbsolutePath, fileType, previous.get(previousAbsolutePath).getSnapshot().getType());
                }

                if (includeAdded) {
                    if (addedIterator == null) {
                        addedIterator = added.iterator();
                    }
                    if (addedIterator.hasNext()) {
                        String currentAbsolutePath = addedIterator.next();
                        return FileChange.added(currentAbsolutePath, fileType, current.get(currentAbsolutePath).getSnapshot().getType());
                    }
                }

                return endOfData();
            }
        };
    }

    private Iterator<TaskStateChange> iterateChangesForRelativePaths(final Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, final String fileType, final boolean includeAdded) {
        final ListMultimap<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousSnapshots = MultimapBuilder.hashKeys().linkedListValues().build();
        for (Entry<String, NormalizedFileSnapshot> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousSnapshots.put(previousSnapshot, new IncrementalFileSnapshotWithAbsolutePath(absolutePath, previousSnapshot.getSnapshot()));
        }
        final Iterator<Entry<String, NormalizedFileSnapshot>> currentEntries = current.entrySet().iterator();
        return new AbstractIterator<TaskStateChange>() {
            private Iterator<Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>> unaccountedForPreviousSnapshotsIterator;
            private final ListMultimap<String, IncrementalFileSnapshotWithAbsolutePath> addedFiles = MultimapBuilder.hashKeys().linkedListValues().build();
            private Iterator<IncrementalFileSnapshotWithAbsolutePath> addedFilesIterator;

            @Override
            protected TaskStateChange computeNext() {
                while (currentEntries.hasNext()) {
                    Entry<String, NormalizedFileSnapshot> entry = currentEntries.next();
                    String currentAbsolutePath = entry.getKey();
                    NormalizedFileSnapshot currentNormalizedSnapshot = entry.getValue();
                    FileContentSnapshot currentSnapshot = currentNormalizedSnapshot.getSnapshot();
                    List<IncrementalFileSnapshotWithAbsolutePath> previousSnapshotsForNormalizedPath = unaccountedForPreviousSnapshots.get(currentNormalizedSnapshot);
                    if (previousSnapshotsForNormalizedPath.isEmpty()) {
                        IncrementalFileSnapshotWithAbsolutePath currentSnapshotWithAbsolutePath = new IncrementalFileSnapshotWithAbsolutePath(currentAbsolutePath, currentSnapshot);
                        addedFiles.put(currentNormalizedSnapshot.getNormalizedPath(), currentSnapshotWithAbsolutePath);
                    } else {
                        IncrementalFileSnapshotWithAbsolutePath previousSnapshotWithAbsolutePath = previousSnapshotsForNormalizedPath.remove(0);
                        FileContentSnapshot previousSnapshot = previousSnapshotWithAbsolutePath.getSnapshot();
                        if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                            return FileChange.modified(currentAbsolutePath, fileType, previousSnapshot.getType(), currentSnapshot.getType());
                        }
                    }
                }

                // Create a single iterator to use for all of the still unaccounted files
                if (unaccountedForPreviousSnapshotsIterator == null) {
                    if (unaccountedForPreviousSnapshots.isEmpty()) {
                        unaccountedForPreviousSnapshotsIterator = Iterators.emptyIterator();
                    } else {
                        List<Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>> entries = Lists.newArrayList(unaccountedForPreviousSnapshots.entries());
                        Collections.sort(entries, ENTRY_COMPARATOR);
                        unaccountedForPreviousSnapshotsIterator = entries.iterator();
                    }
                }

                if (unaccountedForPreviousSnapshotsIterator.hasNext()) {
                    Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousSnapshotEntry = unaccountedForPreviousSnapshotsIterator.next();
                    NormalizedFileSnapshot previousSnapshot = unaccountedForPreviousSnapshotEntry.getKey();
                    String normalizedPath = previousSnapshot.getNormalizedPath();
                    List<IncrementalFileSnapshotWithAbsolutePath> addedFilesForNormalizedPath = addedFiles.get(normalizedPath);
                    if (!addedFilesForNormalizedPath.isEmpty()) {
                        // There might be multiple files with the same normalized path, here we choose one of them
                        IncrementalFileSnapshotWithAbsolutePath modifiedSnapshot = addedFilesForNormalizedPath.remove(0);
                        return FileChange.modified(modifiedSnapshot.getAbsolutePath(), fileType, previousSnapshot.getSnapshot().getType(), modifiedSnapshot.getSnapshot().getType());
                    } else {
                        IncrementalFileSnapshotWithAbsolutePath removedSnapshot = unaccountedForPreviousSnapshotEntry.getValue();
                        return FileChange.removed(removedSnapshot.getAbsolutePath(), fileType, removedSnapshot.getSnapshot().getType());
                    }
                }

                if (includeAdded) {
                    // Create a single iterator to use for all of the added files
                    if (addedFilesIterator == null) {
                        addedFilesIterator = addedFiles.values().iterator();
                    }

                    if (addedFilesIterator.hasNext()) {
                        IncrementalFileSnapshotWithAbsolutePath addedFile = addedFilesIterator.next();
                        return FileChange.added(addedFile.getAbsolutePath(), fileType, addedFile.getSnapshot().getType());
                    }
                }

                return endOfData();
            }
        };
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots) {
        List<NormalizedFileSnapshot> normalizedSnapshots = Lists.newArrayList(snapshots);
        Collections.sort(normalizedSnapshots);
        for (NormalizedFileSnapshot normalizedSnapshot : normalizedSnapshots) {
            normalizedSnapshot.appendToHasher(hasher);
        }
    }

    private static class IncrementalFileSnapshotWithAbsolutePath {
        private final String absolutePath;
        private final FileContentSnapshot snapshot;

        public IncrementalFileSnapshotWithAbsolutePath(String absolutePath, FileContentSnapshot snapshot) {
            this.absolutePath = absolutePath;
            this.snapshot = snapshot;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public FileContentSnapshot getSnapshot() {
            return snapshot;
        }

        @Override
        public String toString() {
            return String.format("%s (%s)", getSnapshot(), absolutePath);
        }
    }
}
