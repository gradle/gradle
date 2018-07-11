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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import static com.google.common.collect.Iterators.emptyIterator;
import static com.google.common.collect.Iterators.singletonIterator;

public enum FingerprintCompareStrategy {
    ABSOLUTE(new AbsolutePathFingerprintCompareStrategy()),
    NORMALIZED(new NormalizedPathFingerprintCompareStrategy()),
    IGNORED_PATH(new IgnoredPathCompareStrategy()),
    CLASSPATH(new ClasspathCompareStrategy());

    private final Impl delegate;

    FingerprintCompareStrategy(FingerprintCompareStrategy.Impl compareStrategy) {
        this.delegate = compareStrategy;
    }

    interface Impl {
        boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded);
        void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots);
    }

    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        // Handle trivial cases with 0 or 1 elements in both current and previous
        Iterator<TaskStateChange> trivialResult = compareTrivialSnapshots(current, previous, propertyTitle, includeAdded);
        if (trivialResult != null) {
            while (trivialResult.hasNext()) {
                if (!visitor.visitChange(trivialResult.next())) {
                    return false;
                }
            }
            return true;
        }
        return delegate.visitChangesSince(visitor, current, previous, propertyTitle, includeAdded);
    }
    public void appendToHasher(BuildCacheHasher hasher, Map<String, NormalizedFileSnapshot> snapshots) {
        delegate.appendToHasher(hasher, snapshots.values());
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
    @Nullable
    static Iterator<TaskStateChange> compareTrivialSnapshots(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String fileType, boolean includeAdded) {
        switch (current.size()) {
            case 0:
                switch (previous.size()) {
                    case 0:
                        return emptyIterator();
                    case 1:
                        Map.Entry<String, NormalizedFileSnapshot> entry = previous.entrySet().iterator().next();
                        TaskStateChange change = FileChange.removed(entry.getKey(), fileType, entry.getValue().getType());
                        return singletonIterator(change);
                    default:
                        return null;
                }

            case 1:
                switch (previous.size()) {
                    case 0:
                        if (includeAdded) {
                            Map.Entry<String, NormalizedFileSnapshot> entry = current.entrySet().iterator().next();
                            TaskStateChange change = FileChange.added(entry.getKey(), fileType, entry.getValue().getType());
                            return singletonIterator(change);
                        } else {
                            return emptyIterator();
                        }
                    case 1:
                        Map.Entry<String, NormalizedFileSnapshot> previousEntry = previous.entrySet().iterator().next();
                        Map.Entry<String, NormalizedFileSnapshot> currentEntry = current.entrySet().iterator().next();
                        return compareTrivialSnapshotEntries(currentEntry, previousEntry, fileType, includeAdded);
                    default:
                        return null;
                }

            default:
                return null;
        }
    }

    private static Iterator<TaskStateChange> compareTrivialSnapshotEntries(Map.Entry<String, NormalizedFileSnapshot> currentEntry, Map.Entry<String, NormalizedFileSnapshot> previousEntry, String fileType, boolean includeAdded) {
        NormalizedFileSnapshot normalizedPrevious = previousEntry.getValue();
        NormalizedFileSnapshot normalizedCurrent = currentEntry.getValue();
        if (normalizedCurrent.getNormalizedPath().equals(normalizedPrevious.getNormalizedPath())) {
            HashCode previousContent = normalizedPrevious.getNormalizedContentHash();
            HashCode currentContent = normalizedCurrent.getNormalizedContentHash();
            if (!currentContent.equals(previousContent)) {
                String path = currentEntry.getKey();
                TaskStateChange change = FileChange.modified(path, fileType, normalizedPrevious.getType(), normalizedCurrent.getType());
                return singletonIterator(change);
            } else {
                return emptyIterator();
            }
        } else {
            if (includeAdded) {
                String previousPath = previousEntry.getKey();
                String currentPath = currentEntry.getKey();
                TaskStateChange remove = FileChange.removed(previousPath, fileType, normalizedPrevious.getType());
                TaskStateChange add = FileChange.added(currentPath, fileType, normalizedCurrent.getType());
                return Iterators.forArray(remove, add);
            } else {
                String path = previousEntry.getKey();
                TaskStateChange change = FileChange.removed(path, fileType, previousEntry.getValue().getType());
                return singletonIterator(change);
            }
        }
    }
}
