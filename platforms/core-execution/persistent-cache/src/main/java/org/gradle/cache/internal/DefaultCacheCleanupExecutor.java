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

import org.apache.commons.io.FileUtils;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CleanableStore;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class DefaultCacheCleanupExecutor implements CacheCleanupExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheCleanupExecutor.class);

    private final CleanableStore cleanableStore;
    private final File gcFile;
    private final CacheCleanupStrategy cacheCleanupStrategy;
    private final BuildOperationRunner buildOperationRunner;

    public DefaultCacheCleanupExecutor(CleanableStore cleanableStore, File gcFile, CacheCleanupStrategy cacheCleanupStrategy, BuildOperationRunner buildOperationRunner) {
        this.cleanableStore = cleanableStore;
        this.gcFile = gcFile;
        this.cacheCleanupStrategy = cacheCleanupStrategy;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public void cleanup() {
        getLastCleanupTime()
            .ifPresent(this::performCleanupIfNecessary);
    }

    private void performCleanupIfNecessary(Instant lastCleanupTime) {
        if (LOGGER.isDebugEnabled()) {
            Duration timeSinceLastCleanup = Duration.between(lastCleanupTime, Instant.now());
            LOGGER.debug("{} has last been fully cleaned up {} hours ago", cleanableStore.getDisplayName(), timeSinceLastCleanup.toHours());
        }

        if (!cacheCleanupStrategy.getCleanupFrequency().requiresCleanup(lastCleanupTime)) {
            LOGGER.debug("Skipping cleanup for {} as it is not yet due", cleanableStore.getDisplayName());
            return;
        }

        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                DefaultCleanupProgressMonitor progressMonitor = new DefaultCleanupProgressMonitor(context);
                Timer timer = Time.startTimer();
                try {
                    cacheCleanupStrategy.getCleanupAction().clean(cleanableStore, progressMonitor);
                    FileUtils.touch(gcFile);
                    LOGGER.info("{} cleaned up in {}.", cleanableStore.getDisplayName(), timer.getElapsed());
                    context.setResult(new CacheCleanupResult(progressMonitor.getDeleted(), lastCleanupTime));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Clean up " + cleanableStore.getDisplayName())
                    .details(new CacheCleanupDetails(cleanableStore.getBaseDir()));
            }
        });
    }

    private Optional<Instant> getLastCleanupTime() {
        // If the cleanup strategy is NO_CLEANUP, we don't need to do anything
        if (cacheCleanupStrategy == CacheCleanupStrategy.NO_CLEANUP) {
            return Optional.empty();
        }

        File dir = cleanableStore.getBaseDir();
        if (!dir.exists()) {
            // Directory does not exist, nothing to clean up
            return Optional.empty();
        }

        if (!gcFile.exists()) {
            // If GC file hasn't been created, then this cache hasn't been used before.
            // We create the GC file, but there's nothing to clean up
            try {
                FileUtils.touch(gcFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return Optional.empty();
        }

        return Optional.of(Instant.ofEpochMilli(gcFile.lastModified()));
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
}
