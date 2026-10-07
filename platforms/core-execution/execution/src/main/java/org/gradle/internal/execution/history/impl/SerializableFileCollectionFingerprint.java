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

package org.gradle.internal.execution.history.impl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.hash.HashCode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class SerializableFileCollectionFingerprint implements FileCollectionFingerprint {
    private final Supplier<ImmutableList<RootFingerprint>> rootFingerprints;
    private final HashCode strategyConfigurationHash;
    private final HashCode hash;
    @Nullable
    private Map<String, FileSystemLocationFingerprint> fingerprints;
    @Nullable
    private ImmutableMultimap<String, HashCode> rootHashes;

    public SerializableFileCollectionFingerprint(ImmutableList<RootFingerprint> rootFingerprints, HashCode strategyConfigurationHash, HashCode hash) {
        this(Suppliers.ofInstance(rootFingerprints), strategyConfigurationHash, hash);
    }

    /**
     * Creates a fingerprint whose root fingerprints are resolved on first use.
     *
     * Resolving them may read from the execution history store, which must not happen while the store is being read.
     */
    public SerializableFileCollectionFingerprint(Supplier<ImmutableList<RootFingerprint>> rootFingerprints, HashCode strategyConfigurationHash, HashCode hash) {
        this.rootFingerprints = Suppliers.memoize(rootFingerprints);
        this.strategyConfigurationHash = strategyConfigurationHash;
        this.hash = hash;
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> getFingerprints() {
        if (fingerprints == null) {
            fingerprints = RootFingerprint.mergeFingerprints(getRootFingerprints());
        }
        return fingerprints;
    }

    @Override
    public ImmutableMultimap<String, HashCode> getRootHashes() {
        if (rootHashes == null) {
            rootHashes = RootFingerprint.rootHashesOf(getRootFingerprints());
        }
        return rootHashes;
    }

    @Override
    public ImmutableList<RootFingerprint> getRootFingerprints() {
        return rootFingerprints.get();
    }

    @Override
    public HashCode getHash() {
        return hash;
    }

    @Override
    public boolean wasCreatedWithStrategy(FingerprintingStrategy strategy) {
        return strategy.getConfigurationHash().equals(strategyConfigurationHash);
    }

    public HashCode getStrategyConfigurationHash() {
        return strategyConfigurationHash;
    }
}
