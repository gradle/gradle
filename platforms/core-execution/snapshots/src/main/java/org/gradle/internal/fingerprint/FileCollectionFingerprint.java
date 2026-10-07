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

package org.gradle.internal.fingerprint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.fingerprint.impl.EmptyCurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;

import java.util.Map;

/**
 * An immutable snapshot of some aspects of the contents and meta-data of a collection of files or directories.
 */
public interface FileCollectionFingerprint {

    /**
     * The underlying fingerprints indexed by absolute path.
     */
    Map<String, FileSystemLocationFingerprint> getFingerprints();

    /**
     * The Merkle hashes of the roots which make up this file collection fingerprint.
     */
    ImmutableMultimap<String, HashCode> getRootHashes();

    /**
     * The fingerprints of each root, in the order of the roots.
     *
     * Root fingerprints are shared between fingerprints, see {@link RootFingerprintInterner}.
     */
    ImmutableList<RootFingerprint> getRootFingerprints();

    /**
     * Returns the combined hash of the fingerprints.
     */
    HashCode getHash();

    boolean wasCreatedWithStrategy(FingerprintingStrategy strategy);

    FileCollectionFingerprint EMPTY = new FileCollectionFingerprint() {
        @Override
        public Map<String, FileSystemLocationFingerprint> getFingerprints() {
            return ImmutableSortedMap.of();
        }

        @Override
        public ImmutableMultimap<String, HashCode> getRootHashes() {
            return ImmutableMultimap.of();
        }

        @Override
        public ImmutableList<RootFingerprint> getRootFingerprints() {
            return ImmutableList.of();
        }

        @Override
        public HashCode getHash() {
            return EmptyCurrentFileCollectionFingerprint.SIGNATURE;
        }

        @Override
        public boolean wasCreatedWithStrategy(FingerprintingStrategy strategy) {
            return false;
        }

        @Override
        public String toString() {
            return "EMPTY";
        }
    };
}
