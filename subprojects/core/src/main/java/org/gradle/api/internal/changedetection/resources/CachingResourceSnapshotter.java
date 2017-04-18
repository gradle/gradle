/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.resources;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.TreeSnapshot;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.nativeintegration.filesystem.FileType;

public class CachingResourceSnapshotter implements ResourceSnapshotter {
    private final PersistentIndexedCache<HashCode, HashCode> cache;
    private final ResourceSnapshotter delegate;

    public CachingResourceSnapshotter(ResourceSnapshotter delegate, PersistentIndexedCache<HashCode, HashCode> cache) {
        this.cache = cache;
        this.delegate = delegate;
    }

    @Override
    public void snapshot(TreeSnapshot resource, final SnapshotCollector collector) {
        SnapshottableResource root = resource.getRoot();
        if (isCacheable(root)) {
            FileSnapshot fileSnapshot = (FileSnapshot) root;
            HashCode hash = cache.get(fileSnapshot.getContent().getContentMd5());
            if (hash != null) {
                collector.recordSnapshot(fileSnapshot, hash);
            } else {
                delegate.snapshot(resource, new CachingSnapshotCollector(root, collector, cache));
            }
        } else {
            delegate.snapshot(resource, collector);
        }
    }

    private static boolean isCacheable(SnapshottableResource root) {
        return root instanceof FileSnapshot && root.getType() == FileType.RegularFile;
    }

    private static class CachingSnapshotCollector implements SnapshotCollector {
        private final SnapshottableResource rootResource;
        private final SnapshotCollector delegate;
        private final PersistentIndexedCache<HashCode, HashCode> cache;

        public CachingSnapshotCollector(SnapshottableResource rootResource, SnapshotCollector delegate, PersistentIndexedCache<HashCode, HashCode> cache) {
            this.rootResource = rootResource;
            this.delegate = delegate;
            this.cache = cache;
        }

        @Override
        public void recordSnapshot(SnapshottableResource recordedResource, HashCode hash) {
            if (rootResource == recordedResource) {
                cache.put(rootResource.getContent().getContentMd5(), hash);
            }
            delegate.recordSnapshot(rootResource, hash);
        }

        @Override
        public SnapshotCollector recordSubCollector(SnapshottableResource recordedResource, SnapshotCollector collector) {
            if (rootResource == recordedResource) {
                return delegate.recordSubCollector(rootResource, new CachingHashCollector(rootResource, collector, cache));
            }
            return delegate.recordSubCollector(rootResource, collector);
        }

        @Override
        public HashCode getHash(final NormalizedFileSnapshotCollector snapshotCollector) {
            HashCode hash = delegate.getHash(snapshotCollector);
            if (isCacheable(rootResource)) {
                cache.put(rootResource.getContent().getContentMd5(), hash);
            }
            return hash;
        }
    }

    private static class CachingHashCollector implements SnapshotCollector {
        private final SnapshottableResource resourceToCache;
        private final SnapshotCollector delegate;
        private final PersistentIndexedCache<HashCode, HashCode> cache;

        public CachingHashCollector(SnapshottableResource resourceToCache, SnapshotCollector delegate, PersistentIndexedCache<HashCode, HashCode> cache) {
            this.resourceToCache = resourceToCache;
            this.delegate = delegate;
            this.cache = cache;
        }

        @Override
        public void recordSnapshot(SnapshottableResource resource, HashCode hash) {
            delegate.recordSnapshot(resource, hash);
        }

        @Override
        public SnapshotCollector recordSubCollector(SnapshottableResource resource, SnapshotCollector collector) {
            return delegate.recordSubCollector(resource, collector);
        }

        @Override
        public HashCode getHash(NormalizedFileSnapshotCollector collector) {
            HashCode hash = delegate.getHash(collector);
            cache.put(resourceToCache.getContent().getContentMd5(), hash);
            return hash;
        }
    }
}
