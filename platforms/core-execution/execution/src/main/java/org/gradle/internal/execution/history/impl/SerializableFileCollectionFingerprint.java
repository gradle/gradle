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

import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.hash.HashCode;

import java.util.Map;

public class SerializableFileCollectionFingerprint implements FileCollectionFingerprint {

    private final Map<String, FileSystemLocationFingerprint> fingerprints;
    private final ImmutableMultimap<String, HashCode> rootHashes;
    private final HashCode strategyConfigurationHash;

    public SerializableFileCollectionFingerprint(Map<String, FileSystemLocationFingerprint> fingerprints, ImmutableMultimap<String, HashCode> rootHashes, HashCode strategyConfigurationHash) {
        this.fingerprints = fingerprints;
        this.rootHashes = rootHashes;
        this.strategyConfigurationHash = strategyConfigurationHash;
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

    public HashCode getStrategyConfigurationHash() {
        return strategyConfigurationHash;
    }
}
