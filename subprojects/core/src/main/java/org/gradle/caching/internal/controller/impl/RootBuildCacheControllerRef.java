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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@ServiceScope(Scope.BuildTree.class)
public class RootBuildCacheControllerRef {
    private final RootBuildCacheController rootController = new RootBuildCacheController();

    public BuildCacheController getForRootBuild() {
        return rootController;
    }

    public BuildCacheController getForNonRootBuild() {
        return new NonRootBuildCacheController(rootController);
    }

    public void effectiveControllerAvailable(BuildCacheController buildCacheController, Supplier<BuildCacheController> factory) {
        ((DelegatingBuildCacheController) buildCacheController).effectiveControllerAvailable(factory);
    }

    public void resetState() {
        try {
            rootController.close();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static abstract class DelegatingBuildCacheController implements BuildCacheController {
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

        protected abstract BuildCacheController getDelegate();

        public abstract void effectiveControllerAvailable(Supplier<BuildCacheController> factory);
    }

    private static class RootBuildCacheController extends DelegatingBuildCacheController {
        private final AtomicReference<BuildCacheController> delegate = new AtomicReference<>();

        private RootBuildCacheController() {
            this.delegate.set(NoOpBuildCacheController.INSTANCE);
        }

        @Override
        public void effectiveControllerAvailable(Supplier<BuildCacheController> factory) {
            if (!this.delegate.compareAndSet(NoOpBuildCacheController.INSTANCE, factory.get())) {
                throw new IllegalStateException("Build cache controller already set");
            }
        }

        @Override
        protected BuildCacheController getDelegate() {
            return delegate.get();
        }

        @Override
        public void close() throws IOException {
            BuildCacheController controller = delegate.getAndSet(NoOpBuildCacheController.INSTANCE);
            controller.close();
        }
    }

    private static class NonRootBuildCacheController extends DelegatingBuildCacheController {
        private final RootBuildCacheController rootController;
        private final AtomicReference<BuildCacheController> delegate = new AtomicReference<>();

        private NonRootBuildCacheController(RootBuildCacheController rootController) {
            this.rootController = rootController;
            this.delegate.set(NoOpBuildCacheController.INSTANCE);
        }

        @Override
        public void effectiveControllerAvailable(Supplier<BuildCacheController> factory) {
            BuildCacheController rootController = this.rootController.getDelegate();
            if (rootController != NoOpBuildCacheController.INSTANCE) {
                // The root controller is available, so don't use the build specific controller
                return;
            }
            // Use the build specific controller
            if (!this.delegate.compareAndSet(NoOpBuildCacheController.INSTANCE, factory.get())) {
                throw new IllegalStateException("Build cache controller already set");
            }
        }

        @Override
        protected BuildCacheController getDelegate() {
            BuildCacheController controller = delegate.get();
            if (controller != NoOpBuildCacheController.INSTANCE) {
                return controller;
            }
            return rootController.delegate.get();
        }

        @Override
        public void close() throws IOException {
            BuildCacheController controller = delegate.getAndSet(NoOpBuildCacheController.INSTANCE);
            controller.close();
        }
    }

}
