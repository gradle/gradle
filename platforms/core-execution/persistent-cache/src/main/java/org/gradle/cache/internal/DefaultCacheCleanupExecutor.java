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
import java.util.concurrent.TimeUnit;

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

    private boolean requiresCleanup() {
        File dir = cleanableStore.getBaseDir();
        if (dir.exists() && cacheCleanupStrategy != CacheCleanupStrategy.NO_CLEANUP) {
            if (!gcFile.exists()) {
                try {
                    FileUtils.touch(gcFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                long duration = System.currentTimeMillis() - gcFile.lastModified();
                long timeInHours = TimeUnit.MILLISECONDS.toHours(duration);
                LOGGER.debug("{} has last been fully cleaned up {} hours ago", cleanableStore.getDisplayName(), timeInHours);
                return cacheCleanupStrategy.getCleanupFrequency().requiresCleanup(gcFile.lastModified());
            }
        }
        return false;
    }

    @Override
    public void cleanup() {
        if (requiresCleanup()) {
            buildOperationRunner.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    Timer timer = Time.startTimer();
                    try {
                        cacheCleanupStrategy.getCleanupAction().clean(cleanableStore, new DefaultCleanupProgressMonitor(context));
                        FileUtils.touch(gcFile);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        LOGGER.info("{} cleaned up in {}.", cleanableStore.getDisplayName(), timer.getElapsed());
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Clean up " + cleanableStore.getDisplayName());
                }
            });
        }
    }
}
