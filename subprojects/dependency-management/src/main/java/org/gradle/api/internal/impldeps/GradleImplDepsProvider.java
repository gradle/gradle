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

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class GradleImplDepsProvider implements Closeable {
    public static final String CACHE_KEY = "generated-gradle-jars";
    public static final String CACHE_DISPLAY_NAME = "Generated Gradle JARs cache";
    private static final Logger LOGGER = LoggerFactory.getLogger(GradleImplDepsProvider.class);
    private final String gradleVersion;
    private PersistentCache cache;
    private RelocatedJarCreator relocatedJarCreator;

    public GradleImplDepsProvider(CacheRepository cacheRepository, ProgressLoggerFactory progressLoggerFactory, String gradleVersion) {
        this.relocatedJarCreator = new GradleImplDepsRelocatedJarCreator(progressLoggerFactory);
        this.gradleVersion = gradleVersion;
        cache = cacheRepository
                .cache(CACHE_KEY)
                .withDisplayName(CACHE_DISPLAY_NAME)
                .withLockOptions(mode(FileLockManager.LockMode.None))
                .open();
    }

    public File getFile(final Collection<File> classpath, String name) {
        if (GradleImplDepsJar.isValidIdentifier(name)) {
            final File implDepsJarFile = jarFile(cache, name);

            if (!implDepsJarFile.exists()) {
                cache.useCache(String.format("Generating %s", implDepsJarFile.getName()), new Runnable() {
                    public void run() {
                        if (!implDepsJarFile.exists()) {
                            relocatedJarCreator.create(implDepsJarFile, classpath);
                        }
                    }
                });
            }

            LOGGER.debug("Using Gradle impl deps JAR file: {}", implDepsJarFile);
            return implDepsJarFile;
        }

        LOGGER.warn("The provided name {} does not refer to a valid Gradle impl deps JAR", name);
        return null;
    }

    public void close() {
        cache.close();
    }

    private File jarFile(PersistentCache cache, String name) {
        return new File(cache.getBaseDir(), String.format("gradle-%s-%s.jar", name, gradleVersion));
    }

    enum GradleImplDepsJar {
        API("api"), TEST_KIT("test-kit");

        private static final Map<String, GradleImplDepsJar> IDENTIFIER_MAPPING;
        private final String identifier;

        static {
            IDENTIFIER_MAPPING = new HashMap<String, GradleImplDepsJar>();

            for(GradleImplDepsJar jar : values()) {
                IDENTIFIER_MAPPING.put(jar.getIdentifier(), jar);
            }
        }

        GradleImplDepsJar(String identifier) {
            this.identifier = identifier;
        }

        String getIdentifier() {
            return identifier;
        }

        static boolean isValidIdentifier(String identifier) {
            return IDENTIFIER_MAPPING.containsKey(identifier);
        }

        static Set<String> getAllIdentifiers() {
            return IDENTIFIER_MAPPING.keySet();
        }
    }
}
