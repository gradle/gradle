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
import com.google.common.collect.Multimap;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.fingerprint.NormalizedFileSnapshot;
import org.gradle.internal.hash.HashCode;

import java.util.Map;

public class DefaultCurrentFileCollectionFingerprint implements CurrentFileCollectionFingerprint {

    private final Map<String, NormalizedFileSnapshot> snapshots;
    private final FingerprintCompareStrategy compareStrategy;
    private final FingerprintingStrategy.Identifier identifier;
    private final Iterable<FileSystemSnapshot> roots;
    private final ImmutableMultimap<String, HashCode> rootHashes;
    private HashCode hash;

    public static CurrentFileCollectionFingerprint from(Iterable<FileSystemSnapshot> roots, FingerprintingStrategy strategy) {
        if (Iterables.isEmpty(roots)) {
            return strategy.getIdentifier().getEmptyFingerprint();
        }
        Map<String, NormalizedFileSnapshot> snapshots = strategy.collectSnapshots(roots);
        if (snapshots.isEmpty()) {
            return strategy.getIdentifier().getEmptyFingerprint();
        }
        return new DefaultCurrentFileCollectionFingerprint(snapshots, strategy.getCompareStrategy(), strategy.getIdentifier(), roots);
    }

    private DefaultCurrentFileCollectionFingerprint(Map<String, NormalizedFileSnapshot> snapshots, FingerprintCompareStrategy compareStrategy, FingerprintingStrategy.Identifier identifier, Iterable<FileSystemSnapshot> roots) {
        this.snapshots = snapshots;
        this.compareStrategy = compareStrategy;
        this.identifier = identifier;
        this.roots = roots;

        final ImmutableMultimap.Builder<String, HashCode> builder = ImmutableMultimap.builder();
        visitRoots(new PhysicalSnapshotVisitor() {
            @Override
            public boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                builder.put(directorySnapshot.getAbsolutePath(), directorySnapshot.getHash());
                return false;
            }

            @Override
            public void visit(PhysicalSnapshot fileSnapshot) {
                builder.put(fileSnapshot.getAbsolutePath(), fileSnapshot.getHash());
            }

            @Override
            public void postVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
            }
        });
        this.rootHashes = builder.build();
    }

    @Override
    public boolean visitChangesSince(FileCollectionFingerprint oldFingerprint, String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        if (hasSameRootHashes(oldFingerprint)) {
            return true;
        }
        return compareStrategy.visitChangesSince(visitor, getSnapshots(), oldFingerprint.getSnapshots(), title, includeAdded);
    }

    private boolean hasSameRootHashes(FileCollectionFingerprint oldFingerprint) {
        return Iterables.elementsEqual(rootHashes.entries(), oldFingerprint.getRootHashes().entries());
    }

    @Override
    public HashCode getHash() {
        if (hash == null) {
            DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
            compareStrategy.appendToHasher(hasher, snapshots);
            hash = hasher.hash();
        }
        return hash;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public Multimap<String, HashCode> getRootHashes() {
        return rootHashes;
    }

    @Override
    public FingerprintingStrategy.Identifier getStrategyIdentifier() {
        return identifier;
    }

    @Override
    public void visitRoots(PhysicalSnapshotVisitor visitor) {
        if (roots == null) {
            throw new UnsupportedOperationException("Roots not available.");
        }
        for (FileSystemSnapshot root : roots) {
            root.accept(visitor);
        }
    }

    @Override
    public HistoricalFileCollectionFingerprint archive() {
        return new DefaultHistoricalFileCollectionFingerprint(snapshots, compareStrategy, rootHashes);
    }
}
