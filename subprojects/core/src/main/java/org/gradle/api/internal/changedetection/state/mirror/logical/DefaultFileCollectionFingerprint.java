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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.changedetection.state.EmptyFileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Map;

public class DefaultFileCollectionFingerprint extends AbstractFileCollectionFingerprint implements CurrentFileCollectionFingerprint {

    private final Map<String, NormalizedFileSnapshot> snapshots;
    private final FingerprintCompareStrategy compareStrategy;
    private final Iterable<FileSystemSnapshot> roots;
    private HashCode hash;

    public static CurrentFileCollectionFingerprint from(Iterable<FileSystemSnapshot> roots, FingerprintingStrategy strategy) {
        Map<String, NormalizedFileSnapshot> snapshots = strategy.collectSnapshots(roots);
        if (snapshots.isEmpty()) {
            return EmptyFileCollectionSnapshot.INSTANCE;
        }
        return new DefaultFileCollectionFingerprint(snapshots, strategy.getCompareStrategy(), roots);
    }

    private DefaultFileCollectionFingerprint(Map<String, NormalizedFileSnapshot> snapshots, FingerprintCompareStrategy compareStrategy, @Nullable Iterable<FileSystemSnapshot> roots) {
        this.snapshots = snapshots;
        this.compareStrategy = compareStrategy;
        this.roots = roots;
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
    public void visitRoots(PhysicalSnapshotVisitor visitor) {
        if (roots == null) {
            throw new UnsupportedOperationException("Roots not available.");
        }
        for (FileSystemSnapshot root : roots) {
            root.accept(visitor);
        }
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(getHash());
    }

    protected FingerprintCompareStrategy getCompareStrategy() {
        return compareStrategy;
    }

    @Override
    public HistoricalFileCollectionFingerprint archive() {
        return new DefaultHistoricalFileCollectionFingerprint(snapshots, compareStrategy);
    }
}
