/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.cleanup;

import org.gradle.api.Action;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.IoActions;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.Collections;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultBuildOutputCleanupCache implements BuildOutputCleanupCache {

    private final static String CACHE_DISPLAY_NAME = "Build Output Cleanup Cache";

    private final CacheRepository cacheRepository;
    private final Gradle gradle;
    private final BuildOutputDeleter buildOutputDeleter;
    private final BuildOutputCleanupRegistry buildOutputCleanupRegistry;

    public DefaultBuildOutputCleanupCache(CacheRepository cacheRepository, Gradle gradle, BuildOutputDeleter buildOutputDeleter, BuildOutputCleanupRegistry buildOutputCleanupRegistry) {
        this.cacheRepository = cacheRepository;
        this.gradle = gradle;
        this.buildOutputDeleter = buildOutputDeleter;
        this.buildOutputCleanupRegistry = buildOutputCleanupRegistry;
    }

    @Override
    public void cleanIfStale() {
        IoActions.withResource(createCache(), new Action<PersistentCache>() {
            @Override
            public void execute(PersistentCache cache) {
                final File markerFile = new File(cache.getBaseDir(), "built.bin");

                cache.useCache(new Runnable() {
                    @Override
                    public void run() {
                        boolean markerFileExists = markerFile.exists();

                        if (!markerFileExists) {
                            buildOutputDeleter.delete(buildOutputCleanupRegistry.getOutputs());
                            GFileUtils.touch(markerFile);
                        }
                    }
                });
            }
        });
    }

    protected PersistentCache createCache() {
        return cacheRepository
                .cache(gradle, "buildOutputCleanup")
                .withCrossVersionCache(CacheBuilder.LockTarget.CachePropertiesFile)
                .withDisplayName(CACHE_DISPLAY_NAME)
                .withLockOptions(mode(FileLockManager.LockMode.None).useCrossVersionImplementation())
                .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
                .open();
    }
}
