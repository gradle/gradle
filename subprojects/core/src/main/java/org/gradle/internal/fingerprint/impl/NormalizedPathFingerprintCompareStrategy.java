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

package org.gradle.internal.fingerprint.impl;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.fingerprint.NormalizedFileSnapshot;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Compares by normalized path (relative/name only) and file contents. Order does not matter.
 */
public class NormalizedPathFingerprintCompareStrategy implements FingerprintCompareStrategy.Impl {
    private static final Comparator<Map.Entry<NormalizedFileSnapshot, ?>> ENTRY_COMPARATOR = new Comparator<Map.Entry<NormalizedFileSnapshot, ?>>() {
        @Override
        public int compare(Map.Entry<NormalizedFileSnapshot, ?> o1, Map.Entry<NormalizedFileSnapshot, ?> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    /**
     * Determines changes by:
     *
     * <ul>
     *     <li>Determining which {@link NormalizedFileSnapshot}s are only in the previous or current fingerprint collection.</li>
     *     <li>
     *         For those only in the previous fingerprint collection it checks if some entry with the same normalized path is in the current collection.
     *         If it is, file is reported as modified, if not as removed.
     *     </li>
     *     <li>Finally, if {@code includeAdded} is {@code true}, the remaining fingerprints which are only in the current collection are reported as added.</li>
     * </ul>
     */
    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> currentFingerprints, Map<String, NormalizedFileSnapshot> previousFingerprints, String propertyTitle, boolean includeAdded) {
        ListMultimap<NormalizedFileSnapshot, FilePathWithType> unaccountedForPreviousFiles = MultimapBuilder.hashKeys(previousFingerprints.size()).linkedListValues().build();
        ListMultimap<String, FilePathWithType> addedFilesByNormalizedPath = MultimapBuilder.linkedHashKeys().linkedListValues().build();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : previousFingerprints.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousFiles.put(previousSnapshot, new FilePathWithType(absolutePath, previousSnapshot.getType()));
        }

        for (Map.Entry<String, NormalizedFileSnapshot> entry : currentFingerprints.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            NormalizedFileSnapshot currentSnapshot = entry.getValue();
            List<FilePathWithType> previousFilesForSnapshot = unaccountedForPreviousFiles.get(currentSnapshot);
            if (previousFilesForSnapshot.isEmpty()) {
                addedFilesByNormalizedPath.put(currentSnapshot.getNormalizedPath(), new FilePathWithType(currentAbsolutePath, currentSnapshot.getType()));
            } else {
                previousFilesForSnapshot.remove(0);
            }
        }
        List<Map.Entry<NormalizedFileSnapshot, FilePathWithType>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousFiles.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Map.Entry<NormalizedFileSnapshot, FilePathWithType> unaccountedForPreviousSnapshotEntry : unaccountedForPreviousEntries) {
            NormalizedFileSnapshot previousSnapshot = unaccountedForPreviousSnapshotEntry.getKey();
            String normalizedPath = previousSnapshot.getNormalizedPath();
            List<FilePathWithType> addedFilesForNormalizedPath = addedFilesByNormalizedPath.get(normalizedPath);
            if (!addedFilesForNormalizedPath.isEmpty()) {
                // There might be multiple files with the same normalized path, here we choose one of them
                FilePathWithType addedFile = addedFilesForNormalizedPath.remove(0);
                if (!visitor.visitChange(FileChange.modified(addedFile.getAbsolutePath(), propertyTitle, previousSnapshot.getType(), addedFile.getFileType()))) {
                    return false;
                }
            } else {
                FilePathWithType removedFile = unaccountedForPreviousSnapshotEntry.getValue();
                if (!visitor.visitChange(FileChange.removed(removedFile.getAbsolutePath(), propertyTitle, removedFile.getFileType()))) {
                    return false;
                }
            }
        }

        if (includeAdded) {
            for (FilePathWithType addedFile : addedFilesByNormalizedPath.values()) {
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
