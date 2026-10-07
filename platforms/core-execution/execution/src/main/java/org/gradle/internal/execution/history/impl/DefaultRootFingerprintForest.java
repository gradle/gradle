/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.cache.IndexedCache;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.fingerprint.RootFingerprintInterner;
import org.gradle.internal.hash.HashCode;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultRootFingerprintForest implements RootFingerprintForest {
    private final IndexedCache<HashCode, RootFingerprint> cache;
    private final RootFingerprintInterner interner;
    private final Set<HashCode> persistedKeys = ConcurrentHashMap.newKeySet();

    public DefaultRootFingerprintForest(IndexedCache<HashCode, RootFingerprint> cache, RootFingerprintInterner interner) {
        this.cache = cache;
        this.interner = interner;
    }

    @Override
    public RootFingerprint load(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash) {
        RootFingerprint known = interner.find(rootPath, rootHash, strategyConfigurationHash);
        if (known != null) {
            return known;
        }
        HashCode key = RootFingerprint.key(rootPath, rootHash, strategyConfigurationHash);
        RootFingerprint loaded = cache.getIfPresent(key);
        if (loaded == null) {
            throw new MissingRootFingerprintException(rootPath, rootHash);
        }
        persistedKeys.add(key);
        return interner.intern(rootPath, rootHash, strategyConfigurationHash, () -> loaded);
    }

    @Override
    public void store(FileCollectionFingerprint fingerprint) {
        if (fingerprint.getRootFingerprints().isEmpty()) {
            return;
        }
        HashCode strategyConfigurationHash = ((SerializableFileCollectionFingerprint) fingerprint).getStrategyConfigurationHash();
        for (RootFingerprint rootFingerprint : fingerprint.getRootFingerprints()) {
            HashCode key = rootFingerprint.key(strategyConfigurationHash);
            if (persistedKeys.add(key)) {
                cache.put(key, rootFingerprint);
            }
        }
    }
}
