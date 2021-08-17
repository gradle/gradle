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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;

import javax.annotation.Nullable;
import java.util.Map;

public class DefaultCurrentFileCollectionFingerprint implements CurrentFileCollectionFingerprint {

    private final Map<String, FileSystemLocationFingerprint> fingerprints;
    private final FingerprintHashingStrategy hashingStrategy;
    private final String identifier;
    private final FileSystemSnapshot roots;
    private final ImmutableMultimap<String, HashCode> rootHashes;
    private final HashCode strategyConfigurationHash;
    private HashCode hash;

    public static CurrentFileCollectionFingerprint from(FileSystemSnapshot roots, FingerprintingStrategy strategy, @Nullable  FileCollectionFingerprint candidate) {
        if (roots == FileSystemSnapshot.EMPTY) {
            return strategy.getEmptyFingerprint();
        }

        ImmutableMultimap<String, HashCode> rootHashes = SnapshotUtil.getRootHashes(roots);
        Map<String, FileSystemLocationFingerprint> fingerprints;
        if (candidate != null
            && candidate.wasCreatedWithStrategy(strategy)
            && equalRootHashes(candidate.getRootHashes(), rootHashes)
        ) {
            fingerprints = candidate.getFingerprints();
        } else {
            fingerprints = strategy.collectFingerprints(roots);
        }
        if (fingerprints.isEmpty()) {
            return strategy.getEmptyFingerprint();
        }
        return new DefaultCurrentFileCollectionFingerprint(fingerprints, roots, rootHashes, strategy);
    }

    private static boolean equalRootHashes(ImmutableMultimap<String, HashCode> first, ImmutableMultimap<String, HashCode> second) {
        // We cannot use `first.equals(second)`, since the order of the root hashes matters
        return Iterables.elementsEqual(first.entries(), second.entries());
    }

    private DefaultCurrentFileCollectionFingerprint(
        Map<String, FileSystemLocationFingerprint> fingerprints,
        FileSystemSnapshot roots,
        ImmutableMultimap<String, HashCode> rootHashes,
        FingerprintingStrategy strategy
    ) {
        this.fingerprints = fingerprints;
        this.identifier = strategy.getIdentifier();
        this.hashingStrategy = strategy.getHashingStrategy();
        this.strategyConfigurationHash = strategy.getConfigurationHash();
        this.roots = roots;
        this.rootHashes = rootHashes;
    }

    @Override
    public HashCode getHash() {
        if (hash == null) {
            Hasher hasher = Hashing.newHasher();
            hashingStrategy.appendToHasher(hasher, fingerprints.values());
            hash = hasher.hash();
        }
        return hash;
    }

    @Override
    public boolean isEmpty() {
        // We'd have created an EmptyCurrentFileCollectionFingerprint if there were no file fingerprints
        return false;
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> getFingerprints() {
        return fingerprints;
    }

    @Override
    public ImmutableMultimap<String, HashCode> getRootHashes() {
        return rootHashes;
    }

    @Override
    public boolean wasCreatedWithStrategy(FingerprintingStrategy strategy) {
        return strategy.getConfigurationHash().equals(strategyConfigurationHash);
    }

    @Override
    public String getStrategyIdentifier() {
        return identifier;
    }

    @Override
    public FileSystemSnapshot getSnapshot() {
        return roots;
    }

    @Override
    public FileCollectionFingerprint archive(ArchivedFileCollectionFingerprintFactory factory) {
        return factory.createArchivedFileCollectionFingerprint(fingerprints, rootHashes, strategyConfigurationHash);
    }

    @Override
    public String toString() {
        return identifier + fingerprints;
    }
}
