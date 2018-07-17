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
import org.gradle.internal.hash.HashCode;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class IgnoredPathCompareStrategy implements FingerprintCompareStrategy.Impl {
    private static final Comparator<Map.Entry<HashCode, FileChangeInformation>> ENTRY_COMPARATOR = new Comparator<Map.Entry<HashCode, FileChangeInformation>>() {
        @Override
        public int compare(Map.Entry<HashCode, FileChangeInformation> o1, Map.Entry<HashCode, FileChangeInformation> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        ListMultimap<HashCode, FileChangeInformation> unaccountedForPreviousFiles = MultimapBuilder.hashKeys(previous.size()).linkedListValues().build();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousFiles.put(previousSnapshot.getNormalizedContentHash(), new FileChangeInformation(absolutePath, previousSnapshot.getType()));
        }

        for (Map.Entry<String, NormalizedFileSnapshot> entry : current.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            NormalizedFileSnapshot currentSnapshot = entry.getValue();
            HashCode normalizedContentHash = currentSnapshot.getNormalizedContentHash();
            List<FileChangeInformation> previousFilesForContent = unaccountedForPreviousFiles.get(normalizedContentHash);
            if (previousFilesForContent.isEmpty()) {
                if (includeAdded) {
                    if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentSnapshot.getType()))) {
                        return false;
                    }
                }
            } else {
                previousFilesForContent.remove(0);
            }
        }

        List<Map.Entry<HashCode, FileChangeInformation>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousFiles.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Map.Entry<HashCode, FileChangeInformation> unaccountedForPreviousEntry : unaccountedForPreviousEntries) {
            FileChangeInformation removedFile = unaccountedForPreviousEntry.getValue();
            if (!visitor.visitChange(FileChange.removed(removedFile.getAbsolutePath(), propertyTitle, removedFile.getFileType()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots) {
        NormalizedPathFingerprintCompareStrategy.appendSortedToHasher(hasher, snapshots);
    }
}
