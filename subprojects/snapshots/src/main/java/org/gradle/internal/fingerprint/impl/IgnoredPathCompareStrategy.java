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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.FileChange;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintCompareStrategy;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Compares {@link FileCollectionFingerprint}s ignoring the path.
 */
public class IgnoredPathCompareStrategy extends AbstractFingerprintCompareStrategy {
    public static final FingerprintCompareStrategy INSTANCE = new IgnoredPathCompareStrategy();

    private static final Comparator<Map.Entry<HashCode, FilePathWithType>> ENTRY_COMPARATOR = new Comparator<Map.Entry<HashCode, FilePathWithType>>() {
        @Override
        public int compare(Map.Entry<HashCode, FilePathWithType> o1, Map.Entry<HashCode, FilePathWithType> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    private IgnoredPathCompareStrategy() {
    }

    /**
     * Determines changes by:
     *
     * <ul>
     *     <li>Determining which content fingerprints are only in the previous or current fingerprint collection.</li>
     *     <li>Those only in the previous fingerprint collection are reported as removed.</li>
     *     <li>If {@code includeAdded} is {@code true}, the files with content fingerprints which are only in the current collection are reported as added.</li>
     * </ul>
     */
    @Override
    protected boolean doVisitChangesSince(ChangeVisitor visitor, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous, String propertyTitle, boolean includeAdded) {
        ListMultimap<HashCode, FilePathWithType> unaccountedForPreviousFiles = MultimapBuilder.hashKeys(previous.size()).linkedListValues().build();
        for (Map.Entry<String, FileSystemLocationFingerprint> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            FileSystemLocationFingerprint previousFingerprint = entry.getValue();
            unaccountedForPreviousFiles.put(previousFingerprint.getNormalizedContentHash(), new FilePathWithType(absolutePath, previousFingerprint.getType()));
        }

        for (Map.Entry<String, FileSystemLocationFingerprint> entry : current.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            FileSystemLocationFingerprint currentFingerprint = entry.getValue();
            HashCode normalizedContentHash = currentFingerprint.getNormalizedContentHash();
            List<FilePathWithType> previousFilesForContent = unaccountedForPreviousFiles.get(normalizedContentHash);
            if (previousFilesForContent.isEmpty()) {
                if (includeAdded) {
                    if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentFingerprint.getType()))) {
                        return false;
                    }
                }
            } else {
                previousFilesForContent.remove(0);
            }
        }

        List<Map.Entry<HashCode, FilePathWithType>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousFiles.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Map.Entry<HashCode, FilePathWithType> unaccountedForPreviousEntry : unaccountedForPreviousEntries) {
            FilePathWithType removedFile = unaccountedForPreviousEntry.getValue();
            if (!visitor.visitChange(FileChange.removed(removedFile.getAbsolutePath(), propertyTitle, removedFile.getFileType()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
        NormalizedPathFingerprintCompareStrategy.appendSortedToHasher(hasher, fingerprints);
    }
}
