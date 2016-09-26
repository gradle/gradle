/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.cache.CacheBuilder;

/**
 * InMemoryTaskArtifactCache suitable for non-daemon processes
 *
 * Drops caches on memory pressure and uses 1/2 of the maximum sizes for caches
 *
 * Invalidates all entries at the end of the build after the persistent cache has been flushed to disk.
 *
 */
public class SoftInMemoryTaskArtifactCache extends InMemoryTaskArtifactCache {
    public SoftInMemoryTaskArtifactCache() {
        super(new CacheCapSizer() {
            @Override
            protected int scaleCacheSize(int referenceValue) {
                return super.scaleCacheSize(referenceValue) / 2;
            }
        });
    }

    @Override
    protected void configureCacheHolderCache(CacheBuilder<Object, Object> cacheBuilder) {
        cacheBuilder.softValues();
    }

    @Override
    public void onFlush() {
        super.onFlush();
        // invalidate all caches on flush
        invalidateAll();
    }
}
