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
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import java.util.Collection;
import java.util.Map;

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

    /**
     * @see org.gradle.api.internal.changedetection.state.FileCollectionSnapshot#visitChangesSince(org.gradle.api.internal.changedetection.state.FileCollectionSnapshot, String, boolean, TaskStateChangeVisitor)
     */
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        // Handle trivial cases with 0 or 1 elements in both current and previous
        if (isTrivialComparison(current, previous)) {
            return compareTrivialSnapshots(visitor, current, previous, propertyTitle, includeAdded);
        }
        return delegate.visitChangesSince(visitor, current, previous, propertyTitle, includeAdded);
    }

    @VisibleForTesting
    static boolean isTrivialComparison(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous) {
        return current.isEmpty() || previous.isEmpty() || (current.size() == 1 && previous.size() == 1);
    }

    public void appendToHasher(BuildCacheHasher hasher, Map<String, NormalizedFileSnapshot> snapshots) {
        delegate.appendToHasher(hasher, snapshots.values());
    }

    /**
     * Compares collection fingerprints if one of current or previous are empty or both have at most one element.
     *
     * @param visitor the {@link TaskStateChangeVisitor} receiving the changes.
     * @param current the current state of the snapshot.
     * @param previous the previous state of the snapshot.
     * @param propertyTitle the property title to use when creating the {@link FileChange}.
     * @param includeAdded whether or not to include added files.
     * @return whether the {@link TaskStateChangeVisitor} is looking for further changes. See {@link TaskStateChangeVisitor#visitChange(TaskStateChange)}.
     */
    @VisibleForTesting
    static boolean compareTrivialSnapshots(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        switch (current.size()) {
            case 0:
                switch (previous.size()) {
                    case 0:
                        return true;
                    default:
                        for (Map.Entry<String, NormalizedFileSnapshot> entry : previous.entrySet()) {
                            TaskStateChange change = FileChange.removed(entry.getKey(), propertyTitle, entry.getValue().getType());
                            if (!visitor.visitChange(change)) {
                                return false;
                            }
                        }
                        return true;
                }

            case 1:
                switch (previous.size()) {
                    case 0:
                        return reportAllAdded(visitor, current, propertyTitle, includeAdded);
                    case 1:
                        Map.Entry<String, NormalizedFileSnapshot> previousEntry = previous.entrySet().iterator().next();
                        Map.Entry<String, NormalizedFileSnapshot> currentEntry = current.entrySet().iterator().next();
                        return compareTrivialSnapshotEntries(visitor, currentEntry, previousEntry, propertyTitle, includeAdded);
                    default:
                        throw new AssertionError();
                }

            default:
                if (!previous.isEmpty()) {
                    throw new AssertionError();
                }
                return reportAllAdded(visitor, current, propertyTitle, includeAdded);
        }
    }

    private static boolean reportAllAdded(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, String propertyTitle, boolean includeAdded) {
        if (includeAdded) {
            for (Map.Entry<String, NormalizedFileSnapshot> entry : current.entrySet()) {
                TaskStateChange change = FileChange.added(entry.getKey(), propertyTitle, entry.getValue().getType());
                if (!visitor.visitChange(change)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean compareTrivialSnapshotEntries(TaskStateChangeVisitor visitor, Map.Entry<String, NormalizedFileSnapshot> currentEntry, Map.Entry<String, NormalizedFileSnapshot> previousEntry, String propertyTitle, boolean includeAdded) {
        NormalizedFileSnapshot normalizedPrevious = previousEntry.getValue();
        NormalizedFileSnapshot normalizedCurrent = currentEntry.getValue();
        if (normalizedCurrent.getNormalizedPath().equals(normalizedPrevious.getNormalizedPath())) {
            HashCode previousContent = normalizedPrevious.getNormalizedContentHash();
            HashCode currentContent = normalizedCurrent.getNormalizedContentHash();
            if (!currentContent.equals(previousContent)) {
                String path = currentEntry.getKey();
                TaskStateChange change = FileChange.modified(path, propertyTitle, normalizedPrevious.getType(), normalizedCurrent.getType());
                return visitor.visitChange(change);
            }
            return true;
        } else {
            String previousPath = previousEntry.getKey();
            TaskStateChange remove = FileChange.removed(previousPath, propertyTitle, normalizedPrevious.getType());
            if (includeAdded) {
                String currentPath = currentEntry.getKey();
                TaskStateChange add = FileChange.added(currentPath, propertyTitle, normalizedCurrent.getType());
                return visitor.visitChange(remove) && visitor.visitChange(add);
            } else {
                return visitor.visitChange(remove);
            }
        }
    }
}
