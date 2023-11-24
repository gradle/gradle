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

package org.gradle.caching.internal.controller;

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class NoOpBuildCacheController implements BuildCacheController {

    public static final BuildCacheController INSTANCE = new NoOpBuildCacheController();

    private NoOpBuildCacheController() {
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Optional<BuildCacheLoadResult> load(BuildCacheKey cacheKey, CacheableEntity cacheableEntity) {
        return Optional.empty();
    }

    @Override
    public void store(BuildCacheKey cacheKey, CacheableEntity entity, Map<String, FileSystemSnapshot> snapshots, Duration executionTime) {

    }

    @Override
    public void close() {

    }
}
