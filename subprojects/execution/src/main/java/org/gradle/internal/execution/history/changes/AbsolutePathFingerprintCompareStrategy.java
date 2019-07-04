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

import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compares by absolute paths and file contents. Order does not matter.
 */
public class AbsolutePathFingerprintCompareStrategy extends AbstractFingerprintCompareStrategy {

    public static final FingerprintCompareStrategy INSTANCE = new AbsolutePathFingerprintCompareStrategy();

    private AbsolutePathFingerprintCompareStrategy() {
    }

    @Override
    protected boolean doVisitChangesSince(ChangeVisitor visitor, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous, String propertyTitle) {
        Set<String> unaccountedForPreviousFingerprints = new LinkedHashSet<String>(previous.keySet());

        for (Map.Entry<String, FileSystemLocationFingerprint> currentEntry : current.entrySet()) {
            String currentAbsolutePath = currentEntry.getKey();
            FileSystemLocationFingerprint currentFingerprint = currentEntry.getValue();
            HashCode currentContentHash = currentFingerprint.getNormalizedContentHash();
            if (unaccountedForPreviousFingerprints.remove(currentAbsolutePath)) {
                FileSystemLocationFingerprint previousFingerprint = previous.get(currentAbsolutePath);
                HashCode previousContentHash = previousFingerprint.getNormalizedContentHash();
                if (!currentContentHash.equals(previousContentHash)) {
                    DefaultFileChange modified = DefaultFileChange.modified(currentAbsolutePath, propertyTitle, previousFingerprint.getType(), currentFingerprint.getType(), currentAbsolutePath);
                    if (!visitor.visitChange(modified)) {
                        return false;
                    }
                }
                // else, unchanged; check next file
            } else {
                DefaultFileChange added = DefaultFileChange.added(currentAbsolutePath, propertyTitle, currentFingerprint.getType(), currentAbsolutePath);
                if (!visitor.visitChange(added)) {
                    return false;
                }
            }
        }

        for (String previousAbsolutePath : unaccountedForPreviousFingerprints) {
            DefaultFileChange removed = DefaultFileChange.removed(previousAbsolutePath, propertyTitle, previous.get(previousAbsolutePath).getType(), previousAbsolutePath);
            if (!visitor.visitChange(removed)) {
                return false;
            }
        }
        return true;
    }

}
