/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.internal.controller.impl;

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class RootBuildCacheControllerRef {

    private BuildCacheController buildCacheController;

    public void set(BuildCacheController buildCacheController) {
        // This instance ends up in build/gradle scoped services for nesteds
        // We don't want to invoke close at that time.
        // Instead, close it at the root.
        this.buildCacheController = new CloseShieldBuildCacheController(buildCacheController);
    }

    public BuildCacheController getForNonRootBuild() {
        if (!isSet()) {
            throw new IllegalStateException("Root build cache controller not yet assigned");
        }

        return buildCacheController;
    }

    public boolean isSet() {
        return buildCacheController != null;
    }

    private static class CloseShieldBuildCacheController implements BuildCacheController {
        private final BuildCacheController delegate;

        private CloseShieldBuildCacheController(BuildCacheController delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isEnabled() {
            return delegate.isEnabled();
        }

        @Override
        public Optional<BuildCacheLoadResult> load(BuildCacheKey cacheKey, CacheableEntity cacheableEntity) {
            return delegate.load(cacheKey, cacheableEntity);
        }

        @Override
        public void store(BuildCacheKey cacheKey, CacheableEntity entity, Map<String, FileSystemSnapshot> snapshots, Duration executionTime) {
            delegate.store(cacheKey, entity, snapshots, executionTime);
        }

        @Override
        public void close() {
        }
    }

}
