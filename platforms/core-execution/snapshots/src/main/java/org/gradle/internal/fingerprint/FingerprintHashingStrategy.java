/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.fingerprint;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.hash.Hasher;

import java.util.Collection;

/**
 * Strategy for appending a collection of fingerprints to a hasher.
 */
public enum FingerprintHashingStrategy {
    SORT {
        @Override
        public void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
            ImmutableList<FileSystemLocationFingerprint> sortedFingerprints = ImmutableList.sortedCopyOf(fingerprints);
            appendCollectionToHasherKeepingOrder(hasher, sortedFingerprints);
        }
    },
    KEEP_ORDER {
        @Override
        public void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
            appendCollectionToHasherKeepingOrder(hasher, fingerprints);
        }
    };

    public abstract void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints);

    protected void appendCollectionToHasherKeepingOrder(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
        for (FileSystemLocationFingerprint fingerprint : fingerprints) {
            fingerprint.appendToHasher(hasher);
        }
    }
}
