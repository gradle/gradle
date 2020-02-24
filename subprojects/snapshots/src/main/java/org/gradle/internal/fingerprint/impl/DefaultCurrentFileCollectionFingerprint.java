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
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.util.Map;

public class DefaultCurrentFileCollectionFingerprint implements CurrentFileCollectionFingerprint {

    private final Map<String, FileSystemLocationFingerprint> fingerprints;
    private final FingerprintHashingStrategy hashingStrategy;
    private final String identifier;
    private final Iterable<? extends FileSystemSnapshot> roots;
    private final ImmutableMultimap<String, HashCode> rootHashes;
    private HashCode hash;

    public static CurrentFileCollectionFingerprint from(Iterable<? extends FileSystemSnapshot> roots, FingerprintingStrategy strategy) {
        if (Iterables.isEmpty(roots)) {
            return strategy.getEmptyFingerprint();
        }
        Map<String, FileSystemLocationFingerprint> fingerprints = strategy.collectFingerprints(roots);
        if (fingerprints.isEmpty()) {
            return strategy.getEmptyFingerprint();
        }
        return new DefaultCurrentFileCollectionFingerprint(fingerprints, strategy.getHashingStrategy(), strategy.getIdentifier(), roots);
    }

    private DefaultCurrentFileCollectionFingerprint(Map<String, FileSystemLocationFingerprint> fingerprints, FingerprintHashingStrategy hashingStrategy, String identifier, Iterable<? extends FileSystemSnapshot> roots) {
        this.fingerprints = fingerprints;
        this.hashingStrategy = hashingStrategy;
        this.identifier = identifier;
        this.roots = roots;

        final ImmutableMultimap.Builder<String, HashCode> builder = ImmutableMultimap.builder();
        accept(new FileSystemSnapshotVisitor() {
            @Override
            public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                builder.put(directorySnapshot.getAbsolutePath(), directorySnapshot.getHash());
                return false;
            }

            @Override
            public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                builder.put(fileSnapshot.getAbsolutePath(), fileSnapshot.getHash());
            }

            @Override
            public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            }
        });
        this.rootHashes = builder.build();
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
    public String getStrategyIdentifier() {
        return identifier;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        if (roots == null) {
            throw new UnsupportedOperationException("Roots not available.");
        }
        for (FileSystemSnapshot root : roots) {
            root.accept(visitor);
        }
    }

    @Override
    public String toString() {
        return identifier + fingerprints;
    }
}
