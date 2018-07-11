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

public class IgnoredPathCompareStrategy implements FingerprintCompareStrategy.Impl {
    private static final Comparator<NonNormalizedFileSnapshot> ENTRY_COMPARATOR = new Comparator<NonNormalizedFileSnapshot>() {
        @Override
        public int compare(NonNormalizedFileSnapshot o1, NonNormalizedFileSnapshot o2) {
            return o1.getNormalizedContentHash().compareTo(o2.getNormalizedContentHash());
        }
    };

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        ListMultimap<HashCode, NonNormalizedFileSnapshot> unaccountedForPreviousSnapshots = MultimapBuilder.hashKeys(previous.size()).linkedListValues().build();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousSnapshots.put(previousSnapshot.getNormalizedContentHash(), new NonNormalizedFileSnapshot(absolutePath, previousSnapshot.getType(), previousSnapshot.getNormalizedContentHash()));
        }

        for (Map.Entry<String, NormalizedFileSnapshot> entry : current.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            HashCode currentSnapshot = entry.getValue().getNormalizedContentHash();
            List<NonNormalizedFileSnapshot> previousSnapshotsForContent = unaccountedForPreviousSnapshots.get(currentSnapshot);
            if (previousSnapshotsForContent.isEmpty()) {
                if (includeAdded) {
                    if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, entry.getValue().getType()))) {
                        return false;
                    }
                }
            } else {
                previousSnapshotsForContent.remove(0);
            }
        }

        List<NonNormalizedFileSnapshot> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousSnapshots.values());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (NonNormalizedFileSnapshot unaccountedForPreviousSnapshot : unaccountedForPreviousEntries) {
            if (!visitor.visitChange(FileChange.removed(unaccountedForPreviousSnapshot.getNormalizedPath(), propertyTitle, unaccountedForPreviousSnapshot.getType()))) {
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
