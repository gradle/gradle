/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Map;

public class OverlappingOutputs {
    private final String propertyName;
    private final String overlappedFilePath;

    public OverlappingOutputs(String propertyName, String overlappedFilePath) {
        this.propertyName = propertyName;
        this.overlappedFilePath = overlappedFilePath;
    }

    @Nullable
    public static OverlappingOutputs detect(@Nullable ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
        for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : current.entrySet()) {
            String propertyName = entry.getKey();
            CurrentFileCollectionFingerprint beforeExecution = entry.getValue();
            FileCollectionFingerprint afterPreviousExecution = getFingerprintAfterPreviousExecution(previous, propertyName);
            OverlappingOutputs overlappingOutputs = OverlappingOutputs.detect(propertyName, afterPreviousExecution, beforeExecution);
            if (overlappingOutputs != null) {
                return overlappingOutputs;
            }
        }
        return null;
    }

    private static FileCollectionFingerprint getFingerprintAfterPreviousExecution(@Nullable ImmutableSortedMap<String, FileCollectionFingerprint> previous, String propertyName) {
        if (previous != null) {
            FileCollectionFingerprint afterPreviousExecution = previous.get(propertyName);
            if (afterPreviousExecution != null) {
                return afterPreviousExecution;
            }
        }
        return FileCollectionFingerprint.EMPTY;
    }


    @Nullable
    private static OverlappingOutputs detect(String propertyName, FileCollectionFingerprint previous, CurrentFileCollectionFingerprint before) {
        Map<String, FileSystemLocationFingerprint> previousFingerprints = previous.getFingerprints();
        Map<String, FileSystemLocationFingerprint> beforeFingerprints = before.getFingerprints();

        for (Map.Entry<String, FileSystemLocationFingerprint> beforeEntry : beforeFingerprints.entrySet()) {
            String path = beforeEntry.getKey();
            FileSystemLocationFingerprint beforeFingerprint = beforeEntry.getValue();
            HashCode contentHash = beforeFingerprint.getNormalizedContentHash();
            FileSystemLocationFingerprint previousFingerprint = previousFingerprints.get(path);
            HashCode previousContentHash = previousFingerprint == null ? null : previousFingerprint.getNormalizedContentHash();
            // Missing files can be ignored
            if (beforeFingerprint.getType() != FileType.Missing) {
                if (createdSincePreviousExecution(previousContentHash) || changedSincePreviousExecution(contentHash, previousContentHash)) {
                    return new OverlappingOutputs(propertyName, path);
                }
            }
        }
        return null;
    }

    private static boolean changedSincePreviousExecution(HashCode contentHash, HashCode previousContentHash) {
        // _changed_ since last execution, possibly by another task
        return !contentHash.equals(previousContentHash);
    }

    private static boolean createdSincePreviousExecution(@Nullable HashCode previousContentHash) {
        // created since last execution, possibly by another task
        return previousContentHash == null;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getOverlappedFilePath() {
        return overlappedFilePath;
    }

    public String toString() {
        return String.format("output property '%s' with path '%s'", propertyName, overlappedFilePath);
    }
}
