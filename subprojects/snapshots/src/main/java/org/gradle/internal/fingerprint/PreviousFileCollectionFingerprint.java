/*
 * Copyright 2021 the original author or authors.
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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;

import java.util.Map;

public interface PreviousFileCollectionFingerprint extends FileCollectionFingerprint {
    HashCode SIGNATURE = Hashing.signature(PreviousFileCollectionFingerprint.class);

    PreviousFileCollectionFingerprint EMPTY = new PreviousFileCollectionFingerprint() {
        @Override
        public Map<String, FileSystemLocationFingerprint> getFingerprints() {
            return ImmutableSortedMap.of();
        }

        @Override
        public ImmutableMultimap<String, HashCode> getRootHashes() {
            return ImmutableMultimap.of();
        }

        @Override
        public boolean wasCreatedWithStrategy(FingerprintingStrategy strategy) {
            return false;
        }

        @Override
        public String toString() {
            return "EMPTY";
        }

        @Override
        public HashCode getStrategyConfigurationHash() {
            return SIGNATURE;
        }
    };

    HashCode getStrategyConfigurationHash();
}
