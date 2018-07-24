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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.NormalizedFileSnapshot;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * Strategy to compare two {@link FileCollectionFingerprint}s.
 *
 * The strategy first tries to do a trivial comparison and delegates the more complex cases to a separate implementation.
 */
public enum FingerprintCompareStrategy {
    /**
     * Compares by absolute paths and file contents. Order does not matter.
     */
    ABSOLUTE(new AbsolutePathFingerprintCompareStrategy()),
    /**
     * Compares by normalized path (relative/name only) and file contents. Order does not matter.
     */
    NORMALIZED(new NormalizedPathFingerprintCompareStrategy()),
    /**
     * Compares by file contents only. Order does not matter.
     */
    IGNORED_PATH(new IgnoredPathCompareStrategy()),
    /**
     * Compares by relative path per root. Order matters.
     */
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
     * @see FileCollectionFingerprint#visitChangesSince(FileCollectionFingerprint, String, boolean, TaskStateChangeVisitor)
     */
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        // Handle trivial cases with 0 or 1 elements in both current and previous
        Boolean trivialResult = compareTrivialSnapshots(visitor, current, previous, propertyTitle, includeAdded);
        if (trivialResult != null) {
            return trivialResult;
        }
        return delegate.visitChangesSince(visitor, current, previous, propertyTitle, includeAdded);
    }

    public void appendToHasher(BuildCacheHasher hasher, Map<String, NormalizedFileSnapshot> snapshots) {
        delegate.appendToHasher(hasher, snapshots.values());
    }

    /**
     * Compares collection fingerprints if one of current or previous are empty or both have at most one element.
     *
     * @return {@code null} if the comparison is not trivial.
     * For a trivial comparision returns whether the {@link TaskStateChangeVisitor} is looking for further changes.
     * See {@link TaskStateChangeVisitor#visitChange(TaskStateChange)}.
     */
    @VisibleForTesting
    @Nullable
    static Boolean compareTrivialSnapshots(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
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
                        return null;
                }

            default:
                if (!previous.isEmpty()) {
                    return null;
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
