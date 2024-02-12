/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.jvm.toolchain.internal.InstallationLocation;

import java.io.Closeable;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CachingJvmMetadataDetector implements JvmMetadataDetector, Closeable {

    private static final Logger LOGGER = Logging.getLogger(CachingJvmMetadataDetector.class);

    private final JvmMetadataDetector metadataDetector;
    private final PersistentCache persistentCache;
    private final IndexedCache<File, JvmInstallationMetadata> indexedCache;
    private final Map<File, JvmInstallationMetadata> invalidJavaMetadataMap = Collections.synchronizedMap(new HashMap<>());

    public CachingJvmMetadataDetector(JvmMetadataDetector jvmMetadataDetector, GlobalScopedCacheBuilderFactory globalScopedCacheBuilderFactory) {
        metadataDetector = jvmMetadataDetector;
        persistentCache = globalScopedCacheBuilderFactory
            .createCacheBuilder("toolchainsMetadata")
            .withDisplayName("Toolchains Metadata")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open();
        indexedCache = persistentCache.createIndexedCache("toolchainsCache", File.class, new DefaultSerializer<>());
    }

    @Override
    public JvmInstallationMetadata getMetadata(InstallationLocation javaInstallationLocation) {
        return persistentCache.useCache(() -> {
            File javaHome = javaInstallationLocation.getCanonicalFile();
            JvmInstallationMetadata javaHomeMetadata = null;
            try {
                javaHomeMetadata = invalidJavaMetadataMap.getOrDefault(javaHome, indexedCache.getIfPresent(javaHome));
            } catch (Exception exception) {
                LOGGER.debug("Unable to obtain the toolchain metadata stored from cache", exception);
            } finally {
                if (javaHomeMetadata == null) {
                    javaHomeMetadata = metadataDetector.getMetadata(javaInstallationLocation);
                    if (javaHomeMetadata.isValidInstallation()) {
                        indexedCache.put(javaHome, javaHomeMetadata);
                    } else {
                        invalidJavaMetadataMap.putIfAbsent(javaHome, javaHomeMetadata);
                    }
                }
            }
            return javaHomeMetadata;
        });
    }

    @Override
    public void close() {
        cleanInvalidMetadata();
        persistentCache.close();
    }

    public void cleanInvalidMetadata() {
        synchronized (invalidJavaMetadataMap) {
            invalidJavaMetadataMap.clear();
        }
    }
}
