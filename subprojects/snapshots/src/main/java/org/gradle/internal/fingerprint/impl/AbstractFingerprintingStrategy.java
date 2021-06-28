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
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;

import static org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext.from;

public abstract class AbstractFingerprintingStrategy implements FingerprintingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFingerprintingStrategy.class);

    private final String identifier;
    private final CurrentFileCollectionFingerprint emptyFingerprint;
    private final DirectorySensitivity directorySensitivity;
    private final LineEndingSensitivity lineEndingSensitivity;

    public AbstractFingerprintingStrategy(String identifier, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity) {
        this.identifier = identifier;
        this.emptyFingerprint = new EmptyCurrentFileCollectionFingerprint(identifier);
        this.directorySensitivity = directorySensitivity;
        this.lineEndingSensitivity = lineEndingSensitivity;
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
    protected HashCode getNormalizedContentHash(FileSystemLocationSnapshot snapshot, ResourceHasher normalizedContentHasher) {
        try {
            return lineEndingSensitivity.isCandidate(snapshot) ?
                normalizedContentHasher.hash(from((RegularFileSnapshot) snapshot)) :
                snapshot.getHash();
        } catch (IOException e) {
            LOGGER.debug("Failed to normalize content from {}.  Falling back to non-normalized hash.", snapshot.getAbsolutePath(), e);
            return snapshot.getHash();
        }
    }

    @Override
    public LineEndingSensitivity getLineEndingNormalization() {
        return lineEndingSensitivity;
    }

    @Override
    public DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }
}
