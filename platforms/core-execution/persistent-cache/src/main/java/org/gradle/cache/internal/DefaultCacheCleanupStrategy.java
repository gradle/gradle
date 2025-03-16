/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupFrequency;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.time.Instant;
import java.util.function.Supplier;

class DefaultCacheCleanupStrategy implements CacheCleanupStrategy {
    private final CleanupAction cleanupAction;
    private final Supplier<CleanupFrequency> cleanupFrequency;
    private final BuildOperationRunner buildOperationRunner;

    DefaultCacheCleanupStrategy(CleanupAction cleanupAction, Supplier<CleanupFrequency> cleanupFrequency, BuildOperationRunner buildOperationRunner) {
        this.cleanupAction = cleanupAction;
        this.cleanupFrequency = cleanupFrequency;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public void clean(CleanableStore cleanableStore, Instant lastCleanupTime) {
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                DefaultCleanupProgressMonitor progressMonitor = new DefaultCleanupProgressMonitor(context);
                cleanupAction.clean(cleanableStore, progressMonitor);
                context.setResult(new CacheCleanupResult(progressMonitor.getDeleted(), lastCleanupTime));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Clean up " + cleanableStore.getDisplayName())
                    .details(new CacheCleanupDetails(cleanableStore.getBaseDir()));
            }
        });
    }


    private static class CacheCleanupDetails implements CacheCleanupBuildOperationType.Details {
        private final File cacheLocation;

        public CacheCleanupDetails(File cacheLocation) {
            this.cacheLocation = cacheLocation;
        }

        @Override
        public File getCacheLocation() {
            return cacheLocation;
        }
    }

    private static class CacheCleanupResult implements CacheCleanupBuildOperationType.Result {
        private final long deletedEntriesCount;
        private final Instant previousCleanupTime;

        private CacheCleanupResult(long deletedEntriesCount, Instant previousCleanupTime) {
            this.deletedEntriesCount = deletedEntriesCount;
            this.previousCleanupTime = previousCleanupTime;
        }

        @Override
        public long getDeletedEntriesCount() {
            return deletedEntriesCount;
        }

        @Override
        public Instant getPreviousCleanupTime() {
            return previousCleanupTime;
        }
    }

    @Override
    public CleanupFrequency getCleanupFrequency() {
        return cleanupFrequency.get();
    }
}
