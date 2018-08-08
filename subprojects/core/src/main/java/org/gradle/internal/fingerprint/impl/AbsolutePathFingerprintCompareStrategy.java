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
import org.gradle.internal.fingerprint.FileFingerprint;
import org.gradle.internal.hash.HashCode;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compares by absolute paths and file contents. Order does not matter.
 */
public class AbsolutePathFingerprintCompareStrategy implements FingerprintCompareStrategy.Impl {

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, FileFingerprint> current, Map<String, FileFingerprint> previous, String propertyTitle, boolean includeAdded) {
        Set<String> unaccountedForPreviousFingerprints = new LinkedHashSet<String>(previous.keySet());

        for (Map.Entry<String, FileFingerprint> currentEntry : current.entrySet()) {
            String currentAbsolutePath = currentEntry.getKey();
            FileFingerprint currentFingerprint = currentEntry.getValue();
            HashCode currentContentHash = currentFingerprint.getNormalizedContentHash();
            if (unaccountedForPreviousFingerprints.remove(currentAbsolutePath)) {
                FileFingerprint previousFingerprint = previous.get(currentAbsolutePath);
                HashCode previousContentHash = previousFingerprint.getNormalizedContentHash();
                if (!currentContentHash.equals(previousContentHash)) {
                    if (!visitor.visitChange(FileChange.modified(currentAbsolutePath, propertyTitle, previousFingerprint.getType(), currentFingerprint.getType()))) {
                        return false;
                    }
                }
                // else, unchanged; check next file
            } else if (includeAdded) {
                if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentFingerprint.getType()))) {
                    return false;
                }
            }
        }

        for (String previousAbsolutePath : unaccountedForPreviousFingerprints) {
            if (!visitor.visitChange(FileChange.removed(previousAbsolutePath, propertyTitle, previous.get(previousAbsolutePath).getType()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<FileFingerprint> fingerprints) {
        NormalizedPathFingerprintCompareStrategy.appendSortedToHasher(hasher, fingerprints);
    }
}
