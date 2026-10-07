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

import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.hash.HashCode;

import java.util.HashMap;
import java.util.Map;

public class TestRootFingerprintForest implements RootFingerprintForest {
    private final Map<HashCode, RootFingerprint> roots = new HashMap<>();

    @Override
    public RootFingerprint load(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash) {
        RootFingerprint root = roots.get(RootFingerprint.key(rootPath, rootHash, strategyConfigurationHash));
        if (root == null) {
            throw new MissingRootFingerprintException(rootPath, rootHash);
        }
        return root;
    }

    @Override
    public void store(FileCollectionFingerprint fingerprint) {
        if (fingerprint.getRootFingerprints().isEmpty()) {
            return;
        }
        HashCode strategyConfigurationHash = ((SerializableFileCollectionFingerprint) fingerprint).getStrategyConfigurationHash();
        for (RootFingerprint root : fingerprint.getRootFingerprints()) {
            roots.put(root.key(strategyConfigurationHash), root);
        }
    }

    public Map<HashCode, RootFingerprint> getRoots() {
        return roots;
    }
}
