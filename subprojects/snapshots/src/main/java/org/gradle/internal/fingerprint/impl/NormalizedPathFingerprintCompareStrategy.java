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

import com.google.common.base.Preconditions;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.DefaultFileChange;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintCompareStrategy;
import org.gradle.internal.hash.Hasher;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.Map.Entry.comparingByKey;

/**
 * Compares by normalized path (relative/name only) and file contents. Order does not matter.
 */
public class NormalizedPathFingerprintCompareStrategy extends AbstractFingerprintCompareStrategy {
    public static final FingerprintCompareStrategy INSTANCE = new NormalizedPathFingerprintCompareStrategy();

    private NormalizedPathFingerprintCompareStrategy() {
    }

    /**
     * Determines changes by:
     *
     * <ul>
     *     <li>Determining which {@link FileSystemLocationFingerprint}s are only in the previous or current fingerprint collection.</li>
     *     <li>
     *         For those only in the previous fingerprint collection it checks if some entry with the same normalized path is in the current collection.
     *         If it is, file is reported as modified, if not as removed.
     *     </li>
     *     <li>Finally, {@code includeAdded} is always {@code true}, meaning that the remaining fingerprints which are only in the current collection are reported as added.</li>
     * </ul>
     */
    @Override
    protected boolean doVisitChangesSince(
        ChangeVisitor visitor,
        Map<String, FileSystemLocationFingerprint> currentFingerprints,
        Map<String, FileSystemLocationFingerprint> previousFingerprints,
        String propertyTitle,
        boolean includeAdded
    ) {
        Preconditions.checkArgument(includeAdded);
        return doVisitChangesSince(visitor, currentFingerprints, previousFingerprints, propertyTitle);
    }

    private boolean doVisitChangesSince(
        ChangeVisitor visitor,
        Map<String, FileSystemLocationFingerprint> currentFingerprints,
        Map<String, FileSystemLocationFingerprint> previousFingerprints,
        String propertyTitle
    ) {
        ListMultimap<FileSystemLocationFingerprint, FilePathWithType> unaccountedForPreviousFiles = getUnaccountedForPreviousFingerprints(previousFingerprints, currentFingerprints);
        ListMultimap<String, FilePathWithType> addedFilesByNormalizedPath = getAddedFilesByNormalizedPath(currentFingerprints, unaccountedForPreviousFiles, previousFingerprints);

        Iterator<Entry<FileSystemLocationFingerprint, FilePathWithType>> iterator = unaccountedForPreviousFiles.entries().stream().sorted(comparingByKey()).iterator();
        while (iterator.hasNext()) {
            Entry<FileSystemLocationFingerprint, FilePathWithType> entry = iterator.next();
            FileSystemLocationFingerprint previousFingerprint = entry.getKey();
            FilePathWithType pathWithType = entry.getValue();

            String normalizedPath = previousFingerprint.getNormalizedPath();
            FileType previousFingerprintType = previousFingerprint.getType();

            Change change;
            if (wasModified(addedFilesByNormalizedPath, normalizedPath, pathWithType)) {
                change = modified(propertyTitle, previousFingerprintType, normalizedPath, pathWithType);
            } else {
                change = removed(propertyTitle, normalizedPath, pathWithType);
            }

            if (!visitor.visitChange(change)) {
                return false;
            }
        }

        for (Entry<String, FilePathWithType> entry : addedFilesByNormalizedPath.entries()) {
            Change added = added(propertyTitle, entry);
            if (!visitor.visitChange(added)) {
                return false;
            }
        }
        return true;
    }

    // There might be multiple files with the same normalized path, here we choose one of them (favoring absolute path matches)
    private static boolean wasModified(ListMultimap<String, FilePathWithType> addedFilesByNormalizedPath, String normalizedPath, FilePathWithType pathWithType) {
        return addedFilesByNormalizedPath.get(normalizedPath)
            .removeIf(file ->
                file.getFileType() != FileType.Missing
                    && pathWithType.getAbsolutePath().equals(file.getAbsolutePath())
            );
    }

    private static ListMultimap<FileSystemLocationFingerprint, FilePathWithType> getUnaccountedForPreviousFingerprints(
        Map<String, FileSystemLocationFingerprint> previousFingerprints,
        Map<String, FileSystemLocationFingerprint> currentFingerprints
    ) {
        ListMultimap<FileSystemLocationFingerprint, FilePathWithType> results = MultimapBuilder
            .hashKeys(previousFingerprints.size())
            .linkedListValues()
            .build();
        for (Entry<String, FileSystemLocationFingerprint> entry : previousFingerprints.entrySet()) {
            // skip exact matches
            if (currentFingerprints.entrySet().contains(entry)) {
                continue;
            }

            String absolutePath = entry.getKey();
            FileSystemLocationFingerprint previousFingerprint = entry.getValue();
            FileType previousFingerprintType = previousFingerprint.getType();

            results.put(previousFingerprint, new FilePathWithType(absolutePath, previousFingerprintType));
        }
        return results;
    }

    private static ListMultimap<String, FilePathWithType> getAddedFilesByNormalizedPath(
        Map<String, FileSystemLocationFingerprint> currentFingerprints,
        ListMultimap<FileSystemLocationFingerprint, FilePathWithType> unaccountedForPreviousFiles,
        Map<String, FileSystemLocationFingerprint> previousFingerprints
    ) {
        ListMultimap<String, FilePathWithType> results = MultimapBuilder
            .linkedHashKeys()
            .arrayListValues()
            .build();
        for (Entry<String, FileSystemLocationFingerprint> entry : currentFingerprints.entrySet()) {
            // skip exact matches
            if (previousFingerprints.entrySet().contains(entry)) {
                continue;
            }

            String absolutePath = entry.getKey();
            FileSystemLocationFingerprint currentFingerprint = entry.getValue();
            List<FilePathWithType> previousFilesForFingerprint = unaccountedForPreviousFiles.get(currentFingerprint);
            FileType fingerprintType = currentFingerprint.getType();

            if (previousFilesForFingerprint.isEmpty()) {
                results.put(currentFingerprint.getNormalizedPath(), new FilePathWithType(absolutePath, fingerprintType));
            } else {
                previousFilesForFingerprint.remove(0);
            }
        }
        return results;
    }

    private static Change modified(
        String propertyTitle,
        FileType previousFingerprintType,
        String normalizedPath,
        FilePathWithType modifiedFile
    ) {
        String absolutePath = modifiedFile.getAbsolutePath();
        FileType fileType = modifiedFile.getFileType();
        return DefaultFileChange.modified(absolutePath, propertyTitle, previousFingerprintType, fileType, normalizedPath);
    }

    private static Change removed(
        String propertyTitle,
        String normalizedPath,
        FilePathWithType removedFile
    ) {
        String absolutePath = removedFile.getAbsolutePath();
        FileType fileType = removedFile.getFileType();
        return DefaultFileChange.removed(absolutePath, propertyTitle, fileType, normalizedPath);
    }

    private static Change added(
        String propertyTitle,
        Entry<String, FilePathWithType> addedFilesByNormalizedPathEntries
    ) {
        FilePathWithType addedFile = addedFilesByNormalizedPathEntries.getValue();
        String absolutePath = addedFile.getAbsolutePath();
        FileType fileType = addedFile.getFileType();
        String normalizedPath = addedFilesByNormalizedPathEntries.getKey();
        return DefaultFileChange.added(absolutePath, propertyTitle, fileType, normalizedPath);
    }

    @Override
    public void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
        appendSortedToHasher(hasher, fingerprints);
    }

    public static void appendSortedToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
        List<FileSystemLocationFingerprint> sortedFingerprints = Lists.newArrayList(fingerprints);
        Collections.sort(sortedFingerprints);
        for (FileSystemLocationFingerprint normalizedSnapshot : sortedFingerprints) {
            normalizedSnapshot.appendToHasher(hasher);
        }
    }
}
