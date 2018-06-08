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

import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

class OrderSensitiveTaskFilePropertyCompareStrategy implements TaskFilePropertyCompareStrategy.Impl {

    @Override
    public boolean accept(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> currentSnapshots, Map<String, NormalizedFileSnapshot> previousSnapshots, String propertyTitle, boolean pathIsAbsolute, boolean includeAdded) {
        Iterator<Map.Entry<String, NormalizedFileSnapshot>> currentEntries = currentSnapshots.entrySet().iterator();
        Iterator<Map.Entry<String, NormalizedFileSnapshot>> previousEntries = previousSnapshots.entrySet().iterator();
        while (true) {
            if (currentEntries.hasNext()) {
                Map.Entry<String, NormalizedFileSnapshot> current = currentEntries.next();
                String currentAbsolutePath = current.getKey();
                if (previousEntries.hasNext()) {
                    Map.Entry<String, NormalizedFileSnapshot> previous = previousEntries.next();
                    NormalizedFileSnapshot currentNormalizedSnapshot = current.getValue();
                    NormalizedFileSnapshot previousNormalizedSnapshot = previous.getValue();
                    String currentNormalizedPath = currentNormalizedSnapshot.getNormalizedPath();
                    String previousNormalizedPath = previousNormalizedSnapshot.getNormalizedPath();
                    if (currentNormalizedPath.equals(previousNormalizedPath)) {
                        if (!currentNormalizedSnapshot.getSnapshot().isContentUpToDate(previousNormalizedSnapshot.getSnapshot())) {
                            if (!visitor.visitChange(
                                FileChange.modified(currentAbsolutePath, propertyTitle,
                                    previousNormalizedSnapshot.getSnapshot().getType(),
                                    currentNormalizedSnapshot.getSnapshot().getType()
                                ))) {
                                return false;
                            }
                        }
                    } else {
                        String previousAbsolutePath = previous.getKey();
                        if (!visitor.visitChange(FileChange.removed(previousAbsolutePath, propertyTitle, previousNormalizedSnapshot.getSnapshot().getType()))) {
                            return false;
                        }
                        if (includeAdded) {
                            if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentNormalizedSnapshot.getSnapshot().getType()))) {
                                return false;
                            }
                        }
                    }
                } else {
                    if (includeAdded) {
                        if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, current.getValue().getSnapshot().getType()))) {
                            return false;
                        }
                    }
                }
            } else {
                if (previousEntries.hasNext()) {
                    Map.Entry<String, NormalizedFileSnapshot> previousEntry = previousEntries.next();
                    if (!visitor.visitChange(FileChange.removed(previousEntry.getKey(), propertyTitle, previousEntry.getValue().getSnapshot().getType()))) {
                        return false;
                    }
                } else {
                    return true;
                }
            }
        }
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots) {
        for (NormalizedFileSnapshot normalizedSnapshot : snapshots) {
            normalizedSnapshot.appendToHasher(hasher);
        }
    }
}
