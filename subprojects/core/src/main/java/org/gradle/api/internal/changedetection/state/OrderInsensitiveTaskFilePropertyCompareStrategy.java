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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
    public boolean accept(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean pathIsAbsolute, boolean includeAdded) {
        if (pathIsAbsolute) {
            return acceptForAbsolutePaths(visitor, current, previous, propertyTitle, includeAdded);
        } else {
            return acceptForRelativePaths(visitor, current, previous, propertyTitle, includeAdded);
        }
    }

    /**
     * A more efficient implementation when absolute paths are used.
     */
    private boolean acceptForAbsolutePaths(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        Set<String> unaccountedForPreviousSnapshots = new LinkedHashSet<String>(previous.keySet());

        for (Entry<String, NormalizedFileSnapshot> currentEntry : current.entrySet()) {
            String currentAbsolutePath = currentEntry.getKey();
            NormalizedFileSnapshot currentNormalizedSnapshot = currentEntry.getValue();
            FileContentSnapshot currentSnapshot = currentNormalizedSnapshot.getSnapshot();
            if (unaccountedForPreviousSnapshots.remove(currentAbsolutePath)) {
                NormalizedFileSnapshot previousNormalizedSnapshot = previous.get(currentAbsolutePath);
                FileContentSnapshot previousSnapshot = previousNormalizedSnapshot.getSnapshot();
                if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                    if (!visitor.visitChange(FileChange.modified(currentAbsolutePath, propertyTitle, previousSnapshot.getType(), currentSnapshot.getType()))) {
                        return false;
                    }
                }
                // else, unchanged; check next file
            } else if (includeAdded) {
                if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentSnapshot.getType()))) {
                    return false;
                }
            }
        }

        for (String previousAbsolutePath : unaccountedForPreviousSnapshots) {
            if (!visitor.visitChange(FileChange.removed(previousAbsolutePath, propertyTitle, previous.get(previousAbsolutePath).getSnapshot().getType()))) {
                return false;
            }
        }
        return true;
    }

    private boolean acceptForRelativePaths(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        ListMultimap<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousSnapshots = MultimapBuilder.hashKeys(previous.size()).linkedListValues().build();
        ListMultimap<String, IncrementalFileSnapshotWithAbsolutePath> addedFiles = MultimapBuilder.hashKeys().linkedListValues().build();
        for (Entry<String, NormalizedFileSnapshot> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousSnapshots.put(previousSnapshot, new IncrementalFileSnapshotWithAbsolutePath(absolutePath, previousSnapshot.getSnapshot()));
        }

        for (Entry<String, NormalizedFileSnapshot> entry : current.entrySet()) {
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
                    if (!visitor.visitChange(FileChange.modified(currentAbsolutePath, propertyTitle, previousSnapshot.getType(), currentSnapshot.getType()))) {
                        return false;
                    }
                }
            }
        }

        List<Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousSnapshots.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousSnapshotEntry : unaccountedForPreviousEntries) {
            NormalizedFileSnapshot previousSnapshot = unaccountedForPreviousSnapshotEntry.getKey();
            String normalizedPath = previousSnapshot.getNormalizedPath();
            List<IncrementalFileSnapshotWithAbsolutePath> addedFilesForNormalizedPath = addedFiles.get(normalizedPath);
            if (!addedFilesForNormalizedPath.isEmpty()) {
                // There might be multiple files with the same normalized path, here we choose one of them
                IncrementalFileSnapshotWithAbsolutePath modifiedSnapshot = addedFilesForNormalizedPath.remove(0);
                if (!visitor.visitChange(FileChange.modified(modifiedSnapshot.getAbsolutePath(), propertyTitle, previousSnapshot.getSnapshot().getType(), modifiedSnapshot.getSnapshot().getType()))) {
                    return false;
                }
            } else {
                IncrementalFileSnapshotWithAbsolutePath removedSnapshot = unaccountedForPreviousSnapshotEntry.getValue();
                if (!visitor.visitChange(FileChange.removed(removedSnapshot.getAbsolutePath(), propertyTitle, removedSnapshot.getSnapshot().getType()))) {
                    return false;
                }
            }
        }

        if (includeAdded) {
            for (IncrementalFileSnapshotWithAbsolutePath addedFile : addedFiles.values()) {
                if (!visitor.visitChange(FileChange.added(addedFile.getAbsolutePath(), propertyTitle, addedFile.getSnapshot().getType()))) {
                    return false;
                }
            }
        }
        return true;
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
