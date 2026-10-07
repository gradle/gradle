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

package org.gradle.internal.fingerprint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fingerprints of all entries under one root of a file collection.
 *
 * Instances are shared between every fingerprint that contains the same root with the same content,
 * see {@link RootFingerprintInterner}. Equality is therefore identity.
 */
public final class RootFingerprint {
    private final String rootPath;
    private final HashCode rootHash;
    private final ImmutableMap<String, FileSystemLocationFingerprint> fingerprints;

    public RootFingerprint(String rootPath, HashCode rootHash, ImmutableMap<String, FileSystemLocationFingerprint> fingerprints) {
        this.rootPath = rootPath;
        this.rootHash = rootHash;
        this.fingerprints = fingerprints;
    }

    public String getRootPath() {
        return rootPath;
    }

    /**
     * The hash of the root's file system snapshot.
     */
    public HashCode getRootHash() {
        return rootHash;
    }

    /**
     * The fingerprints under this root, indexed by absolute path, in visiting order.
     */
    public ImmutableMap<String, FileSystemLocationFingerprint> getFingerprints() {
        return fingerprints;
    }

    /**
     * Identifies this root fingerprint across all fingerprints captured with the given strategy configuration.
     */
    public HashCode key(HashCode strategyConfigurationHash) {
        return key(rootPath, rootHash, strategyConfigurationHash);
    }

    public static HashCode key(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash) {
        Hasher hasher = Hashing.newHasher();
        hasher.putString(rootPath);
        hasher.putHash(rootHash);
        hasher.putHash(strategyConfigurationHash);
        return hasher.hash();
    }

    /**
     * The fingerprints of all roots as one map, in root order.
     *
     * An entry that appears under several roots belongs to the first root, as when all roots are fingerprinted at once.
     */
    public static Map<String, FileSystemLocationFingerprint> mergeFingerprints(ImmutableList<RootFingerprint> rootFingerprints) {
        if (rootFingerprints.size() == 1) {
            return rootFingerprints.get(0).getFingerprints();
        }
        Map<String, FileSystemLocationFingerprint> merged = new LinkedHashMap<>();
        for (RootFingerprint rootFingerprint : rootFingerprints) {
            for (Map.Entry<String, FileSystemLocationFingerprint> entry : rootFingerprint.getFingerprints().entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    public static ImmutableMultimap<String, HashCode> rootHashesOf(ImmutableList<RootFingerprint> rootFingerprints) {
        ImmutableListMultimap.Builder<String, HashCode> builder = ImmutableListMultimap.builder();
        for (RootFingerprint rootFingerprint : rootFingerprints) {
            builder.put(rootFingerprint.getRootPath(), rootFingerprint.getRootHash());
        }
        return builder.build();
    }

    @Override
    public String toString() {
        return rootPath + "@" + rootHash + fingerprints;
    }
}
