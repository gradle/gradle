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

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

public class RootBuildCacheControllerRef {

    private ReferenceCountingBuildCacheController buildCacheController;

    public BuildCacheController register(BuildCacheController buildCacheController) {
        return new ReferenceCountingBuildCacheController(buildCacheController);
    }

    public void set(BuildCacheController buildCacheController) {
        // This instance ends up in build/gradle scoped services for nesteds
        // We don't want to invoke close at that time.
        // Instead, close it at as soon as all the builds using it have finished.
        this.buildCacheController = (ReferenceCountingBuildCacheController) buildCacheController;
    }

    public BuildCacheController getForNonRootBuild() {
        if (!isSet()) {
            throw new IllegalStateException("Root build cache controller not yet assigned");
        }
        buildCacheController.newReference();

        return buildCacheController;
    }

    public boolean isSet() {
        return buildCacheController != null;
    }

    private static class ReferenceCountingBuildCacheController implements BuildCacheController {
        private final BuildCacheController delegate;
        private final AtomicInteger referenceCount = new AtomicInteger(1);

        private ReferenceCountingBuildCacheController(BuildCacheController delegate) {
            this.delegate = delegate;
        }

        @Override
        @Nullable
        public <T> T load(BuildCacheLoadCommand<T> command) {
            return delegate.load(command);
        }

        @Override
        public void store(BuildCacheStoreCommand command) {
            delegate.store(command);
        }

        public void newReference() {
            referenceCount.incrementAndGet();
        }

        @Override
        public void close() {
            int activeReferences = referenceCount.decrementAndGet();
            if (activeReferences == 0) {
                delegate.close();
            }
        }
    }

}
