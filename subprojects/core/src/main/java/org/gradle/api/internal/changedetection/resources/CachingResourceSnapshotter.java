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
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.SnapshottableFileSystemResource;
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
    public void snapshot(Snapshottable snapshottable, final SnapshottingResultRecorder recorder) {
        if (isCacheable(snapshottable)) {
            SnapshottableFileSystemResource fileSystemResource = (SnapshottableFileSystemResource) snapshottable;
            HashCode hash = cache.get(fileSystemResource.getContent().getContentMd5());
            if (hash != null) {
                recorder.recordResult(fileSystemResource, hash);
            } else {
                delegate.snapshot(snapshottable, new CachingSnapshottingResultRecorder(fileSystemResource, recorder, cache));
            }
        } else {
            delegate.snapshot(snapshottable, recorder);
        }
    }

    private static boolean isCacheable(Snapshottable snapshottable) {
        return snapshottable instanceof SnapshottableFileSystemResource && ((SnapshottableFileSystemResource) snapshottable).getType() == FileType.RegularFile;
    }

    private static class CachingSnapshottingResultRecorder implements SnapshottingResultRecorder {
        private final SnapshottableResource rootResource;
        private final SnapshottingResultRecorder delegate;
        private final PersistentIndexedCache<HashCode, HashCode> cache;

        public CachingSnapshottingResultRecorder(SnapshottableResource rootResource, SnapshottingResultRecorder delegate, PersistentIndexedCache<HashCode, HashCode> cache) {
            this.rootResource = rootResource;
            this.delegate = delegate;
            this.cache = cache;
        }

        @Override
        public void recordResult(SnapshottableResource recordedResource, HashCode hash) {
            if (rootResource == recordedResource) {
                cache.put(rootResource.getContent().getContentMd5(), hash);
            }
            delegate.recordResult(rootResource, hash);
        }

        @Override
        public SnapshottingResultRecorder recordCompositeResult(SnapshottableResource recordedResource, SnapshottingResultRecorder recorder) {
            if (rootResource == recordedResource) {
                return delegate.recordCompositeResult(rootResource, new CachingCompositeSnapshottingResultRecorder(rootResource, recorder, cache));
            }
            return delegate.recordCompositeResult(rootResource, recorder);
        }

        @Override
        public HashCode getHash(final NormalizedFileSnapshotCollector collector) {
            HashCode hash = delegate.getHash(collector);
            if (isCacheable(rootResource)) {
                cache.put(rootResource.getContent().getContentMd5(), hash);
            }
            return hash;
        }
    }

    private static class CachingCompositeSnapshottingResultRecorder implements SnapshottingResultRecorder {
        private final SnapshottableResource resourceToCache;
        private final SnapshottingResultRecorder delegate;
        private final PersistentIndexedCache<HashCode, HashCode> cache;

        public CachingCompositeSnapshottingResultRecorder(SnapshottableResource resourceToCache, SnapshottingResultRecorder delegate, PersistentIndexedCache<HashCode, HashCode> cache) {
            this.resourceToCache = resourceToCache;
            this.delegate = delegate;
            this.cache = cache;
        }

        @Override
        public void recordResult(SnapshottableResource resource, HashCode hash) {
            delegate.recordResult(resource, hash);
        }

        @Override
        public SnapshottingResultRecorder recordCompositeResult(SnapshottableResource resource, SnapshottingResultRecorder recorder) {
            return delegate.recordCompositeResult(resource, recorder);
        }

        @Override
        public HashCode getHash(NormalizedFileSnapshotCollector collector) {
            HashCode hash = delegate.getHash(collector);
            cache.put(resourceToCache.getContent().getContentMd5(), hash);
            return hash;
        }
    }
}
