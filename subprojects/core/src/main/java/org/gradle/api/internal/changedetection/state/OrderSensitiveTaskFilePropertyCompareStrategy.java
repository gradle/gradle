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
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

class OrderSensitiveTaskFilePropertyCompareStrategy implements TaskFilePropertyCompareStrategy.Impl {

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, final String fileType, boolean isPathAbsolute, final boolean includeAdded) {
        final Iterator<Map.Entry<String, NormalizedFileSnapshot>> currentEntries = current.entrySet().iterator();
        final Iterator<Map.Entry<String, NormalizedFileSnapshot>> previousEntries = previous.entrySet().iterator();
        return new AbstractIterator<TaskStateChange>() {
            private TaskStateChange remaining;

            @Override
            protected TaskStateChange computeNext() {
                if (remaining != null) {
                    TaskStateChange next = this.remaining;
                    remaining = null;
                    return next;
                }

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
                                    return FileChange.modified(currentAbsolutePath, fileType,
                                        previousNormalizedSnapshot.getSnapshot().getType(),
                                        currentNormalizedSnapshot.getSnapshot().getType());
                                }
                            } else {
                                String previousAbsolutePath = previous.getKey();
                                if (includeAdded) {
                                    remaining = FileChange.added(currentAbsolutePath, fileType, currentNormalizedSnapshot.getSnapshot().getType());
                                }
                                return FileChange.removed(previousAbsolutePath, fileType, previousNormalizedSnapshot.getSnapshot().getType());
                            }
                        } else {
                            if (includeAdded) {
                                return FileChange.added(currentAbsolutePath, fileType, current.getValue().getSnapshot().getType());
                            }
                        }
                    } else {
                        if (previousEntries.hasNext()) {
                            Map.Entry<String, NormalizedFileSnapshot> previousEntry = previousEntries.next();
                            return FileChange.removed(previousEntry.getKey(), fileType, previousEntry.getValue().getSnapshot().getType());
                        } else {
                            return endOfData();
                        }
                    }
                }
            }
        };
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots) {
        for (NormalizedFileSnapshot normalizedSnapshot : snapshots) {
            normalizedSnapshot.appendToHasher(hasher);
        }
    }
}
