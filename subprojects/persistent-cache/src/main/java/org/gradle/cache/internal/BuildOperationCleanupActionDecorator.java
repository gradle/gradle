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

package org.gradle.cache.internal;

import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class BuildOperationCleanupActionDecorator implements CleanupActionDecorator {
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationCleanupActionDecorator(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public CleanupAction decorate(CleanupAction action) {
        return new BuildOperationCacheCleanupDecorator(action, buildOperationExecutor);
    }

    private static class BuildOperationCacheCleanupDecorator implements CleanupAction {
        private final BuildOperationExecutor buildOperationExecutor;
        private final CleanupAction delegate;

        public BuildOperationCacheCleanupDecorator(CleanupAction delegate, BuildOperationExecutor buildOperationExecutor) {
            this.buildOperationExecutor = buildOperationExecutor;
            this.delegate = delegate;
        }

        @Override
        public void clean(final CleanableStore persistentCache, final CleanupProgressMonitor progressMonitor) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    delegate.clean(persistentCache, progressMonitor);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Clean up " + persistentCache);
                }
            });
        }
    }
}
