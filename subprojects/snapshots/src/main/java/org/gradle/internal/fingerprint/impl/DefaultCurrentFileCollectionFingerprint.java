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
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintCompareStrategy;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.util.Map;

public class DefaultCurrentFileCollectionFingerprint implements CurrentFileCollectionFingerprint {

    private final Map<String, FileSystemLocationFingerprint> fingerprints;
    private final FingerprintCompareStrategy compareStrategy;
    private final String identifier;
    private final Iterable<FileSystemSnapshot> roots;
    private final ImmutableMultimap<String, HashCode> rootHashes;
    private HashCode hash;

    public static CurrentFileCollectionFingerprint from(Iterable<FileSystemSnapshot> roots, FingerprintingStrategy strategy) {
        if (Iterables.isEmpty(roots)) {
            return strategy.getEmptyFingerprint();
        }
        Map<String, FileSystemLocationFingerprint> snapshots = strategy.collectFingerprints(roots);
        if (snapshots.isEmpty()) {
            return strategy.getEmptyFingerprint();
        }
        return new DefaultCurrentFileCollectionFingerprint(snapshots, strategy.getCompareStrategy(), strategy.getIdentifier(), roots);
    }

    private DefaultCurrentFileCollectionFingerprint(Map<String, FileSystemLocationFingerprint> fingerprints, FingerprintCompareStrategy compareStrategy, String identifier, Iterable<FileSystemSnapshot> roots) {
        this.fingerprints = fingerprints;
        this.compareStrategy = compareStrategy;
        this.identifier = identifier;
        this.roots = roots;

        final ImmutableMultimap.Builder<String, HashCode> builder = ImmutableMultimap.builder();
        accept(new FileSystemSnapshotVisitor() {
            @Override
            public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                builder.put(directorySnapshot.getAbsolutePath(), directorySnapshot.getHash());
                return false;
            }

            @Override
            public void visit(FileSystemLocationSnapshot fileSnapshot) {
                builder.put(fileSnapshot.getAbsolutePath(), fileSnapshot.getHash());
            }

            @Override
            public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
            }
        });
        this.rootHashes = builder.build();
    }

    @Override
    public boolean visitChangesSince(FileCollectionFingerprint oldFingerprint, String title, boolean includeAdded, ChangeVisitor visitor) {
        if (hasSameRootHashes(oldFingerprint)) {
            return true;
        }
        return compareStrategy.visitChangesSince(visitor, getFingerprints(), oldFingerprint.getFingerprints(), title, includeAdded);
    }

    private boolean hasSameRootHashes(FileCollectionFingerprint oldFingerprint) {
        return Iterables.elementsEqual(rootHashes.entries(), oldFingerprint.getRootHashes().entries());
    }

    @Override
    public HashCode getHash() {
        if (hash == null) {
            Hasher hasher = Hashing.newHasher();
            compareStrategy.appendToHasher(hasher, fingerprints.values());
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
}
