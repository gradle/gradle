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

package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.RegularFileSnapshot;

/**
 * A {@link ResourceSnapshotterCacheService} that delegates to the global service for immutable files
 * and uses the local service for all other files. This ensures optimal cache utilization.
 */
public class SplitResourceSnapshotterCacheService implements ResourceSnapshotterCacheService {
    private final ResourceSnapshotterCacheService globalCache;
    private final ResourceSnapshotterCacheService localCache;
    private final GlobalCacheLocations globalCacheLocations;

    public SplitResourceSnapshotterCacheService(ResourceSnapshotterCacheService globalCache, ResourceSnapshotterCacheService localCache, GlobalCacheLocations globalCacheLocations) {
        this.globalCache = globalCache;
        this.localCache = localCache;
        this.globalCacheLocations = globalCacheLocations;
    }

    @Override
    public HashCode hashFile(RegularFileSnapshot fileSnapshot, RegularFileHasher hasher, HashCode configurationHash) {
        if (globalCacheLocations.isInsideGlobalCache(fileSnapshot.getAbsolutePath())) {
            return globalCache.hashFile(fileSnapshot, hasher, configurationHash);
        } else {
            return localCache.hashFile(fileSnapshot, hasher, configurationHash);
        }
    }
}
