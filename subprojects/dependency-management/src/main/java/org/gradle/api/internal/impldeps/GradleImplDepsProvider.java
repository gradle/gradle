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

package org.gradle.api.internal.impldeps;

import com.google.common.collect.ImmutableSet;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.Set;

public class GradleImplDepsProvider implements Closeable {
    public static final String CACHE_KEY = "generated-gradle-jars";
    public static final Set<String> VALID_JAR_NAMES = ImmutableSet.of("api", "test-kit");
    private static final Logger LOGGER = LoggerFactory.getLogger(GradleImplDepsProvider.class);
    private final CacheRepository cacheRepository;
    private final Object lock = new Object();
    private PersistentCache gradleImplDepsCache;
    private RelocatedJarCreator relocatedJarCreator = new GradleImplDepsRelocatedJarCreator();

    public GradleImplDepsProvider(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    public File getFile(Collection<File> classpath, String name) {
        if (VALID_JAR_NAMES.contains(name)) {
            synchronized (lock) {
                if (gradleImplDepsCache == null) {
                    gradleImplDepsCache = cacheRepository.cache(CACHE_KEY).open();
                }

                File implDepsJarFile = jarFile(gradleImplDepsCache, name);

                if (!implDepsJarFile.exists()) {
                    relocatedJarCreator.create(implDepsJarFile, classpath);
                }

                LOGGER.debug("Using Gradle impl deps JAR file: {}", implDepsJarFile);
                return implDepsJarFile;
            }
        }

        LOGGER.warn("The provided name {} does not refer to a valid Gradle impl deps JAR", name);
        return null;
    }

    public void close() {
        synchronized (lock) {
            try {
                if (gradleImplDepsCache != null) {
                    gradleImplDepsCache.close();
                }
            } finally {
                gradleImplDepsCache = null;
            }
        }
    }

    private File jarFile(PersistentCache cache, String name) {
        return new File(cache.getBaseDir(), String.format("gradle-%s.jar", name));
    }

    void setRelocatedJarCreator(RelocatedJarCreator relocatedJarCreator) {
        this.relocatedJarCreator = relocatedJarCreator;
    }
}
