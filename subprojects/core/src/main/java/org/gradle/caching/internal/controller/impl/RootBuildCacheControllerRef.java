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

package org.gradle.caching.internal.controller.impl;

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.NoOpBuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@ServiceScope(Scope.BuildTree.class)
public class RootBuildCacheControllerRef {

    private final DelegatingBuildCacheController sharedInstance = new DelegatingBuildCacheController();

    public void set(BuildCacheController buildCacheController) {
        sharedInstance.set(buildCacheController);
    }

    public BuildCacheController getForNonRootBuild() {
        return sharedInstance;
    }

    private static class DelegatingBuildCacheController implements BuildCacheController {
        private final AtomicReference<BuildCacheController> delegate = new AtomicReference<>();

        private DelegatingBuildCacheController() {
            this.delegate.set(NoOpBuildCacheController.INSTANCE);
        }

        void set(BuildCacheController buildCacheController) {
            if (!this.delegate.compareAndSet(NoOpBuildCacheController.INSTANCE, buildCacheController)) {
                throw new IllegalStateException("Build cache controller already set");
            }
        }

        @Override
        public boolean isEnabled() {
            return getDelegate().isEnabled();
        }

        @Override
        public Optional<BuildCacheLoadResult> load(BuildCacheKey cacheKey, CacheableEntity cacheableEntity) {
            return getDelegate().load(cacheKey, cacheableEntity);
        }

        @Override
        public void store(BuildCacheKey cacheKey, CacheableEntity entity, Map<String, FileSystemSnapshot> snapshots, Duration executionTime) {
            getDelegate().store(cacheKey, entity, snapshots, executionTime);
        }

        private BuildCacheController getDelegate() {
            return delegate.get();
        }

        @Override
        public void close() {
            // This instance ends up in build/gradle scoped services for nesteds
            // We don't want to invoke close at that time.
            // Instead, close it at the root.
        }
    }

}
