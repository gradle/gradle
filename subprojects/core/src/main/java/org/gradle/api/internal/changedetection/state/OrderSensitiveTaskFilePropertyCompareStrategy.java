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
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheKeyBuilder;

import java.util.Iterator;
import java.util.Map;

class OrderSensitiveTaskFilePropertyCompareStrategy implements TaskFilePropertyCompareStrategy.Impl {

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, final String fileType, boolean isPathAbsolute) {
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
                        String absolutePath = current.getKey();
                        if (previousEntries.hasNext()) {
                            Map.Entry<String, NormalizedFileSnapshot> other = previousEntries.next();
                            NormalizedFileSnapshot normalizedSnapshot = current.getValue();
                            NormalizedFileSnapshot otherNormalizedSnapshot = other.getValue();
                            String normalizedPath = normalizedSnapshot.getNormalizedPath();
                            String otherNormalizedPath = otherNormalizedSnapshot.getNormalizedPath();
                            if (normalizedPath.equals(otherNormalizedPath)) {
                                if (normalizedSnapshot.getSnapshot().isContentUpToDate(otherNormalizedSnapshot.getSnapshot())) {
                                    continue;
                                } else {
                                    return new FileChange(absolutePath, ChangeType.MODIFIED, fileType);
                                }
                            } else {
                                String otherAbsolutePath = other.getKey();
                                remaining = new FileChange(absolutePath, ChangeType.ADDED, fileType);
                                return new FileChange(otherAbsolutePath, ChangeType.REMOVED, fileType);
                            }
                        } else {
                            return new FileChange(absolutePath, ChangeType.ADDED, fileType);
                        }
                    } else {
                        if (previousEntries.hasNext()) {
                            return new FileChange(previousEntries.next().getKey(), ChangeType.REMOVED, fileType);
                        } else {
                            return endOfData();
                        }
                    }
                }
            }
        };
    }

    @Override
    public void appendToCacheKey(BuildCacheKeyBuilder builder, Map<String, NormalizedFileSnapshot> snapshots) {
        for (Map.Entry<String, NormalizedFileSnapshot> entry : snapshots.entrySet()) {
            NormalizedFileSnapshot normalizedSnapshot = entry.getValue();
            normalizedSnapshot.appendToCacheKey(builder);
        }
    }

    @Override
    public boolean isIncludeAdded() {
        return true;
    }
}
