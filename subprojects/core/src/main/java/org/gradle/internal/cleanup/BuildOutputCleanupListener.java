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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class BuildOutputCleanupListener implements ModelConfigurationListener, Closeable {

    private static final Logger LOGGER = Logging.getLogger(DefaultBuildOutputCleanupRegistry.class);
    private final PersistentCache cache;
    private final BuildOutputCleanupRegistry buildOutputCleanupRegistry;
    private final File markerFile;

    public BuildOutputCleanupListener(CacheRepository cacheRepository, File cacheBaseDir, BuildOutputCleanupRegistry buildOutputCleanupRegistry) {
        this.cache = createCache(cacheRepository, cacheBaseDir);
        this.buildOutputCleanupRegistry = buildOutputCleanupRegistry;
        markerFile = new File(cache.getBaseDir(), "built.bin");
    }

    private PersistentCache createCache(CacheRepository cacheRepository, File cacheBaseDir) {
        return cacheRepository
                .cache(cacheBaseDir)
                .withCrossVersionCache(CacheBuilder.LockTarget.CachePropertiesFile)
                .withDisplayName("persisted gradle version state cache")
                .withLockOptions(mode(FileLockManager.LockMode.None).useCrossVersionImplementation())
                .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
                .open();
    }

    @Override
    public void onConfigure(GradleInternal model) {
        cache.useCache("build cleanup cache", new Runnable() {
            @Override
            public void run() {
                boolean markerFileExists = markerFile.exists();

                if (!markerFileExists) {
                    for (File output : buildOutputCleanupRegistry.getOutputs()) {
                        deleteOutput(output);
                    }

                    GFileUtils.touch(markerFile);
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    private void deleteOutput(File output) {
        try {
            if (output.isDirectory() && output.list().length > 0) {
                GFileUtils.cleanDirectory(output);
                LOGGER.quiet(String.format("Cleaned up directory '%s'", output));
            } else if (output.isFile()) {
                GFileUtils.forceDelete(output);
                LOGGER.quiet(String.format("Cleaned up file '%s'", output));
            }
        } catch (UncheckedIOException e) {
            LOGGER.warn(String.format("Unable to clean up '%s'", output), e);
        }
    }
}
