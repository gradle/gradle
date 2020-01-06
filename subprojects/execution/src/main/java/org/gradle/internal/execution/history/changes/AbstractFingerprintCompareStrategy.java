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

package org.gradle.internal.execution.history.changes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Map;

public abstract class AbstractFingerprintCompareStrategy implements FingerprintCompareStrategy {

    @Override
    public boolean visitChangesSince(FileCollectionFingerprint current, FileCollectionFingerprint previous, String propertyTitle, ChangeVisitor visitor) {
        if (hasSameRootHashes(current, previous)) {
            return true;
        }
        return visitChangesSince(current.getFingerprints(), previous.getFingerprints(), propertyTitle, visitor);
    }

    private boolean hasSameRootHashes(FileCollectionFingerprint current, FileCollectionFingerprint previous) {
        return Iterables.elementsEqual(current.getRootHashes().entries(), previous.getRootHashes().entries());
    }

    private boolean visitChangesSince(Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous, String propertyTitle, ChangeVisitor visitor) {
        // Handle trivial cases with 0 or 1 elements in both current and previous
        Boolean trivialResult = compareTrivialFingerprints(visitor, current, previous, propertyTitle);
        if (trivialResult != null) {
            return trivialResult;
        }
        return doVisitChangesSince(visitor, current, previous, propertyTitle);
    }

    protected abstract boolean doVisitChangesSince(ChangeVisitor visitor, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous, String propertyTitle);

    /**
     * Compares collection fingerprints if one of current or previous are empty or both have at most one element.
     *
     * @return {@code null} if the comparison is not trivial.
     * For a trivial comparision returns whether the {@link ChangeVisitor} is looking for further changes.
     * See {@link ChangeVisitor#visitChange(Change)}.
     */
    @VisibleForTesting
    @Nullable
    static Boolean compareTrivialFingerprints(ChangeVisitor visitor, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous, String propertyTitle) {
        switch (current.size()) {
            case 0:
                switch (previous.size()) {
                    case 0:
                        return true;
                    default:
                        for (Map.Entry<String, FileSystemLocationFingerprint> entry : previous.entrySet()) {
                            Change change = DefaultFileChange.removed(entry.getKey(), propertyTitle, entry.getValue().getType(), entry.getValue().getNormalizedPath());
                            if (!visitor.visitChange(change)) {
                                return false;
                            }
                        }
                        return true;
                }

            case 1:
                switch (previous.size()) {
                    case 0:
                        return reportAllAdded(visitor, current, propertyTitle);
                    case 1:
                        Map.Entry<String, FileSystemLocationFingerprint> previousEntry = previous.entrySet().iterator().next();
                        Map.Entry<String, FileSystemLocationFingerprint> currentEntry = current.entrySet().iterator().next();
                        return compareTrivialFingerprintEntries(visitor, currentEntry, previousEntry, propertyTitle);
                    default:
                        return null;
                }

            default:
                if (!previous.isEmpty()) {
                    return null;
                }
                return reportAllAdded(visitor, current, propertyTitle);
        }
    }

    private static boolean reportAllAdded(ChangeVisitor visitor, Map<String, FileSystemLocationFingerprint> current, String propertyTitle) {
        for (Map.Entry<String, FileSystemLocationFingerprint> entry : current.entrySet()) {
            Change change = DefaultFileChange.added(entry.getKey(), propertyTitle, entry.getValue().getType(), entry.getValue().getNormalizedPath());
            if (!visitor.visitChange(change)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareTrivialFingerprintEntries(ChangeVisitor visitor, Map.Entry<String, FileSystemLocationFingerprint> currentEntry, Map.Entry<String, FileSystemLocationFingerprint> previousEntry, String propertyTitle) {
        FileSystemLocationFingerprint previousFingerprint = previousEntry.getValue();
        FileSystemLocationFingerprint currentFingerprint = currentEntry.getValue();
        if (currentFingerprint.getNormalizedPath().equals(previousFingerprint.getNormalizedPath())) {
            HashCode previousContent = previousFingerprint.getNormalizedContentHash();
            HashCode currentContent = currentFingerprint.getNormalizedContentHash();
            if (!currentContent.equals(previousContent)) {
                String path = currentEntry.getKey();
                Change change = DefaultFileChange.modified(path, propertyTitle, previousFingerprint.getType(), currentFingerprint.getType(), currentFingerprint.getNormalizedPath());
                return visitor.visitChange(change);
            }
            return true;
        } else {
            String previousPath = previousEntry.getKey();
            Change remove = DefaultFileChange.removed(previousPath, propertyTitle, previousFingerprint.getType(), previousFingerprint.getNormalizedPath());
            String currentPath = currentEntry.getKey();
            Change add = DefaultFileChange.added(currentPath, propertyTitle, currentFingerprint.getType(), currentFingerprint.getNormalizedPath());
            return visitor.visitChange(remove) && visitor.visitChange(add);
        }
    }
}
