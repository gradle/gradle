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

import com.google.common.collect.Lists;
import org.gradle.internal.hash.Hasher;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public enum FingerprintHashingStrategy {
    SORTED {
        @Override
        public void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
            List<FileSystemLocationFingerprint> sortedFingerprints = Lists.newArrayList(fingerprints);
            Collections.sort(sortedFingerprints);
            for (FileSystemLocationFingerprint normalizedSnapshot : sortedFingerprints) {
                normalizedSnapshot.appendToHasher(hasher);
            }
        }
    },
    ORDERED {
        @Override
        public void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints) {
            for (FileSystemLocationFingerprint fingerprint : fingerprints) {
                fingerprint.appendToHasher(hasher);
            }
        }
    };

    public abstract void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints);
}
