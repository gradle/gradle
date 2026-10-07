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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.fingerprint.RootFingerprintInterner;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class DefaultCurrentFileCollectionFingerprint implements CurrentFileCollectionFingerprint {

    private final ImmutableList<RootFingerprint> rootFingerprints;
    private final FingerprintHashingStrategy hashingStrategy;
    private final String identifier;
    private final FileSystemSnapshot roots;
    private final HashCode strategyConfigurationHash;
    @Nullable
    private Map<String, FileSystemLocationFingerprint> fingerprints;
    @Nullable
    private ImmutableMultimap<String, HashCode> rootHashes;
    @Nullable
    private HashCode hash;

    public static CurrentFileCollectionFingerprint from(
        FileSystemSnapshot roots,
        FingerprintingStrategy strategy,
        RootFingerprintInterner interner,
        @Nullable FileCollectionFingerprint candidate
    ) {
        if (roots == FileSystemSnapshot.EMPTY) {
            return strategy.getEmptyFingerprint();
        }
        HashCode strategyConfigurationHash = strategy.getConfigurationHash();
        ImmutableList.Builder<RootFingerprint> builder = ImmutableList.builder();
        roots.roots().forEach(root -> builder.add(fingerprintRoot(root, strategy, strategyConfigurationHash, interner)));
        ImmutableList<RootFingerprint> rootFingerprints = builder.build();
        if (rootFingerprints.stream().allMatch(rootFingerprint -> rootFingerprint.getFingerprints().isEmpty())) {
            return strategy.getEmptyFingerprint();
        }
        HashCode knownHash = candidate != null
            && candidate.wasCreatedWithStrategy(strategy)
            && candidate.getRootFingerprints().equals(rootFingerprints)
            ? candidate.getHash()
            : null;
        return new DefaultCurrentFileCollectionFingerprint(rootFingerprints, roots, strategy, knownHash);
    }

    private static RootFingerprint fingerprintRoot(FileSystemLocationSnapshot root, FingerprintingStrategy strategy, HashCode strategyConfigurationHash, RootFingerprintInterner interner) {
        String rootPath = root.getAbsolutePath();
        HashCode rootHash = root.getHash();
        return interner.intern(rootPath, rootHash, strategyConfigurationHash,
            () -> new RootFingerprint(rootPath, rootHash, ImmutableMap.copyOf(strategy.collectFingerprints(root))));
    }

    private DefaultCurrentFileCollectionFingerprint(
        ImmutableList<RootFingerprint> rootFingerprints,
        FileSystemSnapshot roots,
        FingerprintingStrategy strategy,
        @Nullable HashCode hash
    ) {
        this.rootFingerprints = rootFingerprints;
        this.identifier = strategy.getIdentifier();
        this.hashingStrategy = strategy.getHashingStrategy();
        this.strategyConfigurationHash = strategy.getConfigurationHash();
        this.roots = roots;
        this.hash = hash;
    }

    @Override
    public HashCode getHash() {
        if (hash == null) {
            Hasher hasher = Hashing.newHasher();
            hashingStrategy.appendToHasher(hasher, getFingerprints().values());
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
        if (fingerprints == null) {
            fingerprints = RootFingerprint.mergeFingerprints(rootFingerprints);
        }
        return fingerprints;
    }

    @Override
    public ImmutableMultimap<String, HashCode> getRootHashes() {
        if (rootHashes == null) {
            rootHashes = RootFingerprint.rootHashesOf(rootFingerprints);
        }
        return rootHashes;
    }

    @Override
    public ImmutableList<RootFingerprint> getRootFingerprints() {
        return rootFingerprints;
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
        return factory.createArchivedFileCollectionFingerprint(rootFingerprints, strategyConfigurationHash, getHash());
    }

    @Override
    public String toString() {
        return identifier + getFingerprints();
    }
}
