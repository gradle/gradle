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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheKeyBuilder;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.Iterators.emptyIterator;
import static com.google.common.collect.Iterators.singletonIterator;

public enum TaskFilePropertyCompareStrategy {
    ORDERED(new OrderSensitiveTaskFilePropertyCompareStrategy()),
    UNORDERED(new OrderInsensitiveTaskFilePropertyCompareStrategy(true)),
    OUTPUT(new OrderInsensitiveTaskFilePropertyCompareStrategy(false));

    private final Impl delegate;

    TaskFilePropertyCompareStrategy(Impl delegate) {
        this.delegate = delegate;
    }

    public Iterator<TaskStateChange> iterateContentChangesSince(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String fileType, boolean pathIsAbsolute) {
        // Handle trivial cases with 0 or 1 elements in both current and previous
        Iterator<TaskStateChange> trivialResult = compareTrivialSnapshots(current, previous, fileType, delegate.isIncludeAdded());
        if (trivialResult != null) {
            return trivialResult;
        }
        return delegate.iterateContentChangesSince(current, previous, fileType, pathIsAbsolute);
    }

    public void appendToCacheKey(BuildCacheKeyBuilder builder, Map<String, NormalizedFileSnapshot> snapshots) {
        delegate.appendToCacheKey(builder, snapshots);
    }

    interface Impl {
        Iterator<TaskStateChange> iterateContentChangesSince(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String fileType, boolean pathIsAbsolute);
        void appendToCacheKey(BuildCacheKeyBuilder builder, Map<String, NormalizedFileSnapshot> snapshots);
        boolean isIncludeAdded();
    }

    /**
     * Compares snapshot collections if both current and previous states have at most one element.
     *
     * @param current the current state of the snapshot.
     * @param previous the previous state of the snapshot.
     * @param fileType the file type to use when creating the {@link FileChange}.
     * @param includeAdded    whether or not to include added files.
     * @return either a single change representing the change that happened,
     * or {@code null} if there are more than one element in either {@code current}
     * or {@code previous}.
     */
    @VisibleForTesting
    static Iterator<TaskStateChange> compareTrivialSnapshots(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String fileType, boolean includeAdded) {
        switch (current.size()) {
            case 0:
                switch (previous.size()) {
                    case 0:
                        return emptyIterator();
                    case 1:
                        String path = previous.keySet().iterator().next();
                        TaskStateChange change = new FileChange(path, ChangeType.REMOVED, fileType);
                        return singletonIterator(change);
                    default:
                        return null;
                }

            case 1:
                switch (previous.size()) {
                    case 0:
                        if (includeAdded) {
                            String path = current.keySet().iterator().next();
                            TaskStateChange change = new FileChange(path, ChangeType.ADDED, fileType);
                            return singletonIterator(change);
                        } else {
                            return emptyIterator();
                        }
                    case 1:
                        Entry<String, NormalizedFileSnapshot> previousEntry = previous.entrySet().iterator().next();
                        Entry<String, NormalizedFileSnapshot> currentEntry = current.entrySet().iterator().next();
                        return compareTrivialSnapshotEntries(currentEntry, previousEntry, fileType, includeAdded);
                    default:
                        return null;
                }

            default:
                return null;
        }
    }

    private static Iterator<TaskStateChange> compareTrivialSnapshotEntries(Entry<String, NormalizedFileSnapshot> currentEntry, Entry<String, NormalizedFileSnapshot> previousEntry, String fileType, boolean includeAdded) {
        NormalizedFileSnapshot normalizedPrevious = previousEntry.getValue();
        NormalizedFileSnapshot normalizedCurrent = currentEntry.getValue();
        if (normalizedCurrent.getNormalizedPath().equals(normalizedPrevious.getNormalizedPath())) {
            IncrementalFileSnapshot previousSnapshot = normalizedPrevious.getSnapshot();
            IncrementalFileSnapshot currentSnapshot = normalizedCurrent.getSnapshot();
            if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                String path = currentEntry.getKey();
                TaskStateChange change = new FileChange(path, ChangeType.MODIFIED, fileType);
                return singletonIterator(change);
            } else {
                return emptyIterator();
            }
        } else {
            if (includeAdded) {
                String previousPath = previousEntry.getKey();
                String currentPath = currentEntry.getKey();
                TaskStateChange remove = new FileChange(previousPath, ChangeType.REMOVED, fileType);
                TaskStateChange add = new FileChange(currentPath, ChangeType.ADDED, fileType);
                return Iterators.forArray(remove, add);
            } else {
                String path = previousEntry.getKey();
                TaskStateChange change = new FileChange(path, ChangeType.REMOVED, fileType);
                return singletonIterator(change);
            }
        }
    }
}
