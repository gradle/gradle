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

import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.hashing.ConfigurableNormalizer;
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;

public abstract class AbstractFingerprintingStrategy implements FingerprintingStrategy {
    private final String identifier;
    private final CurrentFileCollectionFingerprint emptyFingerprint;
    private final DirectorySensitivity directorySensitivity;
    private final HashCode configurationHash;

    public AbstractFingerprintingStrategy(
        String identifier,
        DirectorySensitivity directorySensitivity,
        ConfigurableNormalizer contentNormalizer
    ) {
        this.identifier = identifier;
        this.emptyFingerprint = new EmptyCurrentFileCollectionFingerprint(identifier);
        this.directorySensitivity = directorySensitivity;
        Hasher hasher = Hashing.newHasher();
        hasher.putString(getClass().getName());
        contentNormalizer.appendConfigurationToHasher(hasher);
        this.configurationHash = hasher.hash();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public CurrentFileCollectionFingerprint getEmptyFingerprint() {
        return emptyFingerprint;
    }

    @Nullable
    protected HashCode getNormalizedContentHash(FileSystemLocationSnapshot snapshot, FileSystemLocationSnapshotHasher normalizedContentHasher) {
        try {
            return normalizedContentHasher.hash(snapshot);
        } catch (IOException e) {
            throw new UncheckedIOException(failedToNormalize(snapshot), e);
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(failedToNormalize(snapshot), e.getCause());
        }
    }

    private static String failedToNormalize(FileSystemLocationSnapshot snapshot) {
        return String.format("Failed to normalize content of '%s'.", snapshot.getAbsolutePath());
    }

    protected DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }

    @Override
    public HashCode getConfigurationHash() {
        return configurationHash;
    }
}
