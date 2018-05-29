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

import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

public abstract class RootHoldingFileCollectionSnapshot implements FileCollectionSnapshot {
    private final ListMultimap<String, LogicalSnapshot> roots;
    private HashCode hashCode;

    public RootHoldingFileCollectionSnapshot(ListMultimap<String, LogicalSnapshot> roots) {
        this.roots = roots;
    }

    public RootHoldingFileCollectionSnapshot(@Nullable HashCode hashCode) {
        this.hashCode = hashCode;
        this.roots = null;
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

    @Nullable
    public ListMultimap<String, LogicalSnapshot> getRoots() {
        return roots;
    }
}
