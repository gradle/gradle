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

import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileFingerprint;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Compares two {@link FileCollectionFingerprint}s representing classpaths.
 *
 * That means that the comparison happens in-order with relative path sensitivity.
 */
public class ClasspathCompareStrategy implements FingerprintCompareStrategy.Impl {

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, FileFingerprint> currentSnapshots, Map<String, FileFingerprint> previousSnapshots, String propertyTitle, boolean includeAdded) {
        Iterator<Map.Entry<String, FileFingerprint>> currentEntries = currentSnapshots.entrySet().iterator();
        Iterator<Map.Entry<String, FileFingerprint>> previousEntries = previousSnapshots.entrySet().iterator();
        while (true) {
            if (currentEntries.hasNext()) {
                Map.Entry<String, FileFingerprint> current = currentEntries.next();
                String currentAbsolutePath = current.getKey();
                if (previousEntries.hasNext()) {
                    Map.Entry<String, FileFingerprint> previous = previousEntries.next();
                    FileFingerprint currentFingerprint = current.getValue();
                    FileFingerprint previousFingerprint = previous.getValue();
                    String currentNormalizedPath = currentFingerprint.getNormalizedPath();
                    String previousNormalizedPath = previousFingerprint.getNormalizedPath();
                    if (currentNormalizedPath.equals(previousNormalizedPath)) {
                        if (!currentFingerprint.getNormalizedContentHash().equals(previousFingerprint.getNormalizedContentHash())) {
                            if (!visitor.visitChange(
                                FileChange.modified(currentAbsolutePath, propertyTitle,
                                    previousFingerprint.getType(),
                                    currentFingerprint.getType()
                                ))) {
                                return false;
                            }
                        }
                    } else {
                        String previousAbsolutePath = previous.getKey();
                        if (!visitor.visitChange(FileChange.removed(previousAbsolutePath, propertyTitle, previousFingerprint.getType()))) {
                            return false;
                        }
                        if (includeAdded) {
                            if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentFingerprint.getType()))) {
                                return false;
                            }
                        }
                    }
                } else {
                    if (includeAdded) {
                        if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, current.getValue().getType()))) {
                            return false;
                        }
                    }
                }
            } else {
                if (previousEntries.hasNext()) {
                    Map.Entry<String, FileFingerprint> previousEntry = previousEntries.next();
                    if (!visitor.visitChange(FileChange.removed(previousEntry.getKey(), propertyTitle, previousEntry.getValue().getType()))) {
                        return false;
                    }
                } else {
                    return true;
                }
            }
        }
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<FileFingerprint> fingerprints) {
        for (FileFingerprint fileFingerprint : fingerprints) {
            fileFingerprint.appendToHasher(hasher);
        }
    }
}
