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

import com.google.common.base.Preconditions;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Map;

public abstract class SnapshotFactoryFileCollectionSnapshot<T> implements FileCollectionSnapshot {
    private Factory<Map<String, T>> snapshotFactory;
    private Map<String, T> snapshots;
    private HashCode hashCode;

    public SnapshotFactoryFileCollectionSnapshot(Factory<Map<String, T>> snapshotFactory) {
        this.snapshotFactory = snapshotFactory;
    }

    public SnapshotFactoryFileCollectionSnapshot(Map<String, T> snapshots, @Nullable HashCode hashCode) {
        this.snapshots = snapshots;
        this.hashCode = hashCode;
    }

    protected Map<String, T> getFileSnapshots() {
        if (snapshots == null) {
            Preconditions.checkNotNull(snapshotFactory, "Snapshots or a snapshot factory must be provided.");
            snapshots = Preconditions.checkNotNull(snapshotFactory.create());
        }
        return snapshots;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(getHash());
    }

    @Override
    public HashCode getHash() {
        if (hashCode == null) {
            DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
            doGetHash(hasher);
            hashCode = hasher.hash();
        }
        return hashCode;
    }

    protected boolean hasHash() {
        return hashCode != null;
    }

    protected abstract void doGetHash(DefaultBuildCacheHasher hasher);
}
