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

package org.gradle.api.internal.runtimeshaded;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class RuntimeShadedJarFactory implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeShadedJarFactory.class);

    public static final String CACHE_KEY = "generated-gradle-jars";
    public static final String CACHE_DISPLAY_NAME = "Generated Gradle JARs cache";

    private final String gradleVersion;
    private final PersistentCache cache;
    private final RuntimeShadedJarCreator creator;

    public RuntimeShadedJarFactory(CacheRepository cacheRepository, ProgressLoggerFactory progressLoggerFactory, String gradleVersion) {
        this.creator = new RuntimeShadedJarCreator(progressLoggerFactory);
        this.gradleVersion = gradleVersion;
        this.cache = cacheRepository
            .cache(CACHE_KEY)
            .withDisplayName(CACHE_DISPLAY_NAME)
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .open();
    }

    public File get(RuntimeShadedJarType type, final Collection<? extends File> classpath) {
        final File jarFile = jarFile(cache, type);
        if (!jarFile.exists()) {
            cache.useCache("Generating " + jarFile.getName(), new Runnable() {
                public void run() {
                    if (!jarFile.exists()) {
                        creator.create(jarFile, classpath);
                    }
                }
            });
        }

        LOGGER.debug("Using Gradle runtime shaded JAR file: {}", jarFile);
        return jarFile;
    }

    public void close() {
        cache.close();
    }

    private File jarFile(PersistentCache cache, RuntimeShadedJarType type) {
        return new File(cache.getBaseDir(), "gradle-" + type.getIdentifier() + "-" + gradleVersion + ".jar");
    }
}
