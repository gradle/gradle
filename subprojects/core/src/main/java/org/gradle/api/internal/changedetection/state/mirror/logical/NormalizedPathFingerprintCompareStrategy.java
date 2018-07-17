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
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class NormalizedPathFingerprintCompareStrategy implements FingerprintCompareStrategy.Impl {
    private static final Comparator<Map.Entry<NormalizedFileSnapshot, ?>> ENTRY_COMPARATOR = new Comparator<Map.Entry<NormalizedFileSnapshot, ?>>() {
        @Override
        public int compare(Map.Entry<NormalizedFileSnapshot, ?> o1, Map.Entry<NormalizedFileSnapshot, ?> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> currentFingerprints, Map<String, NormalizedFileSnapshot> previousFingerprints, String propertyTitle, boolean includeAdded) {
        ListMultimap<NormalizedFileSnapshot, FileChangeInformation> unaccountedForPreviousFiles = MultimapBuilder.hashKeys(previousFingerprints.size()).linkedListValues().build();
        ListMultimap<String, FileChangeInformation> addedFilesByNormalizedPath = MultimapBuilder.linkedHashKeys().linkedListValues().build();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : previousFingerprints.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousFiles.put(previousSnapshot, new FileChangeInformation(absolutePath, previousSnapshot.getType()));
        }

        for (Map.Entry<String, NormalizedFileSnapshot> entry : currentFingerprints.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            NormalizedFileSnapshot currentSnapshot = entry.getValue();
            List<FileChangeInformation> previousFilesForSnapshot = unaccountedForPreviousFiles.get(currentSnapshot);
            if (previousFilesForSnapshot.isEmpty()) {
                addedFilesByNormalizedPath.put(currentSnapshot.getNormalizedPath(), new FileChangeInformation(currentAbsolutePath, currentSnapshot.getType()));
            } else {
                previousFilesForSnapshot.remove(0);
            }
        }
        List<Map.Entry<NormalizedFileSnapshot, FileChangeInformation>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousFiles.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Map.Entry<NormalizedFileSnapshot, FileChangeInformation> unaccountedForPreviousSnapshotEntry : unaccountedForPreviousEntries) {
            NormalizedFileSnapshot previousSnapshot = unaccountedForPreviousSnapshotEntry.getKey();
            String normalizedPath = previousSnapshot.getNormalizedPath();
            List<FileChangeInformation> addedFilesForNormalizedPath = addedFilesByNormalizedPath.get(normalizedPath);
            if (!addedFilesForNormalizedPath.isEmpty()) {
                // There might be multiple files with the same normalized path, here we choose one of them
                FileChangeInformation addedFile = addedFilesForNormalizedPath.remove(0);
                if (!visitor.visitChange(FileChange.modified(addedFile.getAbsolutePath(), propertyTitle, previousSnapshot.getType(), addedFile.getFileType()))) {
                    return false;
                }
            } else {
                FileChangeInformation removedFile = unaccountedForPreviousSnapshotEntry.getValue();
                if (!visitor.visitChange(FileChange.removed(removedFile.getAbsolutePath(), propertyTitle, removedFile.getFileType()))) {
                    return false;
                }
            }
        }

        if (includeAdded) {
            for (FileChangeInformation addedFile : addedFilesByNormalizedPath.values()) {
                if (!visitor.visitChange(FileChange.added(addedFile.getAbsolutePath(), propertyTitle, addedFile.getFileType()))) {
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
