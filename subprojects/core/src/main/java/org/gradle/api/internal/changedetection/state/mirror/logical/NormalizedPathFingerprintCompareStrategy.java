/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.NonNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class NormalizedPathFingerprintCompareStrategy implements FingerprintCompareStrategy.Impl {
    private static final Comparator<Map.Entry<NormalizedFileSnapshot, NonNormalizedFileSnapshot>> ENTRY_COMPARATOR = new Comparator<Map.Entry<NormalizedFileSnapshot, NonNormalizedFileSnapshot>>() {
        @Override
        public int compare(Map.Entry<NormalizedFileSnapshot, NonNormalizedFileSnapshot> o1, Map.Entry<NormalizedFileSnapshot, NonNormalizedFileSnapshot> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> currentFingerprints, Map<String, NormalizedFileSnapshot> previousFingerprints, String propertyTitle, boolean includeAdded) {
        ListMultimap<NormalizedFileSnapshot, NonNormalizedFileSnapshot> unaccountedForPreviousSnapshots = MultimapBuilder.hashKeys(previousFingerprints.size()).linkedListValues().build();
        ListMultimap<String, NonNormalizedFileSnapshot> addedFiles = MultimapBuilder.linkedHashKeys().linkedListValues().build();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : previousFingerprints.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousSnapshots.put(previousSnapshot, new NonNormalizedFileSnapshot(absolutePath, previousSnapshot.getType(), previousSnapshot.getNormalizedContentHash()));
        }

        for (Map.Entry<String, NormalizedFileSnapshot> entry : currentFingerprints.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            NormalizedFileSnapshot currentSnapshot = entry.getValue();
            List<NonNormalizedFileSnapshot> previousSnapshotsForNormalizedPath = unaccountedForPreviousSnapshots.get(currentSnapshot);
            if (previousSnapshotsForNormalizedPath.isEmpty()) {
                NonNormalizedFileSnapshot currentSnapshotWithAbsolutePath = new NonNormalizedFileSnapshot(currentAbsolutePath, currentSnapshot.getType(), currentSnapshot.getNormalizedContentHash());
                addedFiles.put(currentSnapshot.getNormalizedPath(), currentSnapshotWithAbsolutePath);
            } else {
                NonNormalizedFileSnapshot previousSnapshotWithAbsolutePath = previousSnapshotsForNormalizedPath.remove(0);
                HashCode previousSnapshot = previousSnapshotWithAbsolutePath.getNormalizedContentHash();
                if (!currentSnapshot.getNormalizedContentHash().equals(previousSnapshot)) {
                    if (!visitor.visitChange(FileChange.modified(currentAbsolutePath, propertyTitle, previousSnapshotWithAbsolutePath.getType(), currentSnapshot.getType()))) {
                        return false;
                    }
                }
            }
        }
        List<Map.Entry<NormalizedFileSnapshot, NonNormalizedFileSnapshot>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousSnapshots.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Map.Entry<NormalizedFileSnapshot, NonNormalizedFileSnapshot> unaccountedForPreviousSnapshotEntry : unaccountedForPreviousEntries) {
            NormalizedFileSnapshot previousSnapshot = unaccountedForPreviousSnapshotEntry.getKey();
            String normalizedPath = previousSnapshot.getNormalizedPath();
            List<NonNormalizedFileSnapshot> addedFilesForNormalizedPath = addedFiles.get(normalizedPath);
            if (!addedFilesForNormalizedPath.isEmpty()) {
                // There might be multiple files with the same normalized path, here we choose one of them
                NonNormalizedFileSnapshot modifiedSnapshot = addedFilesForNormalizedPath.remove(0);
                if (!visitor.visitChange(FileChange.modified(modifiedSnapshot.getNormalizedPath(), propertyTitle, previousSnapshot.getType(), modifiedSnapshot.getType()))) {
                    return false;
                }
            } else {
                NonNormalizedFileSnapshot removedSnapshot = unaccountedForPreviousSnapshotEntry.getValue();
                if (!visitor.visitChange(FileChange.removed(removedSnapshot.getNormalizedPath(), propertyTitle, removedSnapshot.getType()))) {
                    return false;
                }
            }
        }

        if (includeAdded) {
            for (NonNormalizedFileSnapshot addedFile : addedFiles.values()) {
                if (!visitor.visitChange(FileChange.added(addedFile.getNormalizedPath(), propertyTitle, addedFile.getType()))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots) {
        appendSortedToHasher(hasher, snapshots);
    }

    public static void appendSortedToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots) {
        List<NormalizedFileSnapshot> normalizedSnapshots = Lists.newArrayList(snapshots);
        Collections.sort(normalizedSnapshots);
        for (NormalizedFileSnapshot normalizedSnapshot : normalizedSnapshots) {
            normalizedSnapshot.appendToHasher(hasher);
        }
    }
}
