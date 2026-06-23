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
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.NoOpBuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.caching.internal.services.BuildCacheControllerFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.util.Path;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This service manages the state of the {@link BuildCacheController} instances in the build tree.
 * A separate instance is created for each build in the tree. This instance delegates to another immutable controller based on the state of the root build.
 *
 * <p>The implementation provides the following behavior:
 *
 * <ul>
 *     <li>All builds in the tree use the root build's cache configuration, once that configuration is available (ie the root build's settings have been evaluated).</li>
 *     <li>If caching is required in non-root build before the root build's configuration is available, that build's own cache configuration will be used.
 *      Once the root build's configuration is available, the build-specific configuration is discarded.
 *     </li>
 *     <li>If caching is required in any build before either the root build's configuration or the build-specific configuration is available, caching is disabled.
 *       Once configuration is available, it will be used, as per the above.
 *     </li>
 * </ul>
 *
 * <p>Currently, there is no simple, general way to know where in the above lifecycle a given piece of work will run.</p>
 */
@ServiceScope(Scope.BuildTree.class)
public class LifecycleAwareBuildCacheControllerFactory {
    private final RootBuildCacheController rootController = new RootBuildCacheController();

    public LifecycleAwareBuildCacheController createForRootBuild(Path identityPath, BuildCacheControllerFactory buildCacheControllerFactory, InstanceGenerator instanceGenerator) {
        return rootController.init(identityPath, buildCacheControllerFactory, instanceGenerator);
    }

    public LifecycleAwareBuildCacheController createForNonRootBuild(Path identityPath, BuildCacheControllerFactory buildCacheControllerFactory, InstanceGenerator instanceGenerator) {
        return rootController.createChild(identityPath, buildCacheControllerFactory, instanceGenerator);
    }

    private static abstract class DelegatingBuildCacheController implements LifecycleAwareBuildCacheController {
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

        @Override
        public void close() {
            resetState();
        }

        protected static void createDelegate(BuildCacheConfigurationInternal configuration, AtomicReference<BuildCacheController> delegate, BuildCacheControllerFactory buildCacheControllerFactory, Path identityPath, InstanceGenerator instanceGenerator) {
            BuildCacheController controller = buildCacheControllerFactory.createController(identityPath, configuration, instanceGenerator);
            if (!delegate.compareAndSet(NoOpBuildCacheController.INSTANCE, controller)) {
                try {
                    controller.close();
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                throw new IllegalStateException("Build cache controller already set");
            }
        }

        protected static void discardDelegate(AtomicReference<BuildCacheController> delegate) {
            BuildCacheController controller = delegate.getAndSet(NoOpBuildCacheController.INSTANCE);
            try {
                controller.close();
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        protected abstract BuildCacheController getDelegate();
    }

    /**
     * This implementation tracks the state of the root build.
     */
    private static class RootBuildCacheController extends DelegatingBuildCacheController {
        // Holds an immutable BCC that represents the current state of the root build's cache configuration.
        private final AtomicReference<BuildCacheController> delegate = new AtomicReference<>();
        private Path identityPath;
        private BuildCacheControllerFactory buildCacheControllerFactory;
        private InstanceGenerator instanceGenerator;

        private RootBuildCacheController() {
            this.delegate.set(NoOpBuildCacheController.INSTANCE);
        }

        public RootBuildCacheController init(Path identityPath, BuildCacheControllerFactory buildCacheControllerFactory, InstanceGenerator instanceGenerator) {
            this.identityPath = identityPath;
            this.buildCacheControllerFactory = buildCacheControllerFactory;
            this.instanceGenerator = instanceGenerator;
            return this;
        }

        @Override
        public void configurationAvailable(BuildCacheConfigurationInternal configuration) {
            createDelegate(configuration, this.delegate, buildCacheControllerFactory, identityPath, instanceGenerator);
        }

        @Override
        protected BuildCacheController getDelegate() {
            return delegate.get();
        }

        @Override
        public void resetState() {
            discardDelegate(delegate);
        }

        public LifecycleAwareBuildCacheController createChild(Path identityPath, BuildCacheControllerFactory buildCacheControllerFactory, InstanceGenerator instanceGenerator) {
            return new NonRootBuildCacheController(this, identityPath, buildCacheControllerFactory, instanceGenerator);
        }
    }

    /**
     * This implementation manages the state of all other builds in the tree.
     */
    private static class NonRootBuildCacheController extends DelegatingBuildCacheController {
        private final RootBuildCacheController rootController;
        // Holds a BCC that represents the current state of this build's cache configuration, but only if used as an 'early' build.
        private final AtomicReference<BuildCacheController> delegate = new AtomicReference<>();
        private final Path identityPath;
        private final BuildCacheControllerFactory buildCacheControllerFactory;
        private final InstanceGenerator instanceGenerator;

        private NonRootBuildCacheController(
            RootBuildCacheController rootController,
            Path identityPath,
            BuildCacheControllerFactory buildCacheControllerFactory,
            InstanceGenerator instanceGenerator
        ) {
            this.rootController = rootController;
            this.identityPath = identityPath;
            this.buildCacheControllerFactory = buildCacheControllerFactory;
            this.instanceGenerator = instanceGenerator;
            this.delegate.set(NoOpBuildCacheController.INSTANCE);
        }

        @Override
        public void configurationAvailable(BuildCacheConfigurationInternal configuration) {
            BuildCacheController rootController = this.rootController.getDelegate();
            if (rootController != NoOpBuildCacheController.INSTANCE) {
                // The root controller is available, so don't use the build specific controller
                return;
            }
            // Use the build specific controller
            createDelegate(configuration, this.delegate, buildCacheControllerFactory, identityPath, instanceGenerator);
        }

        @Override
        protected BuildCacheController getDelegate() {
            BuildCacheController rootController = this.rootController.getDelegate();
            if (rootController != NoOpBuildCacheController.INSTANCE) {
                // The root controller is available, so don't use the build specific controller
                return rootController;
            }
            return delegate.get();
        }

        @Override
        public void resetState() {
            discardDelegate(delegate);
        }
    }
}
