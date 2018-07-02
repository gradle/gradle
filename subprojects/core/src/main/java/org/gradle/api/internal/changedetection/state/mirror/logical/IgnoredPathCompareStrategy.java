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
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class IgnoredPathCompareStrategy implements FingerprintCompareStrategy.Impl {
    private static final Comparator<Map.Entry<FileContentSnapshot, String>> ENTRY_COMPARATOR = new Comparator<Map.Entry<FileContentSnapshot, String>>() {
        @Override
        public int compare(Map.Entry<FileContentSnapshot, String> o1, Map.Entry<FileContentSnapshot, String> o2) {
            return o1.getKey().getContentMd5().compareTo(o2.getKey().getContentMd5());
        }
    };

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        ListMultimap<FileContentSnapshot, String> unaccountedForPreviousSnapshots = MultimapBuilder.hashKeys(previous.size()).linkedListValues().build();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            FileContentSnapshot previousSnapshot = entry.getValue().getSnapshot();
            unaccountedForPreviousSnapshots.put(previousSnapshot, absolutePath);
        }

        for (Map.Entry<String, NormalizedFileSnapshot> entry : current.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            FileContentSnapshot currentSnapshot = entry.getValue().getSnapshot();
            List<String> previousSnapshotsForContent = unaccountedForPreviousSnapshots.get(currentSnapshot);
            if (previousSnapshotsForContent.isEmpty()) {
                if (includeAdded) {
                    if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentSnapshot.getType()))) {
                        return false;
                    }
                }
            } else {
                previousSnapshotsForContent.remove(0);
            }
        }

        List<Map.Entry<FileContentSnapshot, String>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousSnapshots.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Map.Entry<FileContentSnapshot, String> unaccountedForPreviousSnapshotEntry : unaccountedForPreviousEntries) {
            if (!visitor.visitChange(FileChange.removed(unaccountedForPreviousSnapshotEntry.getValue(), propertyTitle, unaccountedForPreviousSnapshotEntry.getKey().getType()))) {
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
