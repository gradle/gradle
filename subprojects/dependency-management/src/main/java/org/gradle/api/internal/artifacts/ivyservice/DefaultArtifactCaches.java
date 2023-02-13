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
package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.internal.IncubationLogger;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.util.List;
import java.util.Optional;

public class DefaultArtifactCaches implements ArtifactCachesProvider {
    private final static Logger LOGGER = Logging.getLogger(DefaultArtifactCaches.class);

    private final DefaultArtifactCacheMetadata writableCacheMetadata;
    private final DefaultArtifactCacheMetadata readOnlyCacheMetadata;
    private final LateInitWritableArtifactCacheLockingAccessCoordinator writableCacheAccessCoordinator;
    private final ReadOnlyArtifactCacheLockingAccessCoordinator readOnlyCacheAccessCoordinator;

    public DefaultArtifactCaches(
            GlobalScopedCacheBuilderFactory cacheBuilderFactory,
            UnscopedCacheBuilderFactory unscopedCacheBuilderFactory,
            WritableArtifactCacheLockingParameters params,
            DocumentationRegistry documentationRegistry,
            CacheConfigurationsInternal cacheConfigurations
                                 ) {
        writableCacheMetadata = new DefaultArtifactCacheMetadata(cacheBuilderFactory);
        writableCacheAccessCoordinator = new LateInitWritableArtifactCacheLockingAccessCoordinator(() -> {
            return new WritableArtifactCacheLockingAccessCoordinator(unscopedCacheBuilderFactory, writableCacheMetadata, params.getFileAccessTimeJournal(), params.getUsedGradleVersions(), cacheConfigurations);
        });
        String roCache = System.getenv(READONLY_CACHE_ENV_VAR);
        if (StringUtils.isNotEmpty(roCache)) {
            IncubationLogger.incubatingFeatureUsed("Shared read-only dependency cache");
            File baseDir = validateReadOnlyCache(documentationRegistry, new File(roCache).getAbsoluteFile());
            if (baseDir != null) {
                readOnlyCacheMetadata = new DefaultArtifactCacheMetadata(cacheBuilderFactory, baseDir);
                readOnlyCacheAccessCoordinator = new ReadOnlyArtifactCacheLockingAccessCoordinator(unscopedCacheBuilderFactory, readOnlyCacheMetadata);
                LOGGER.info("The read-only dependency cache is enabled \nThe {} environment variable was set to {}", READONLY_CACHE_ENV_VAR, baseDir);
            } else {
                readOnlyCacheMetadata = null;
                readOnlyCacheAccessCoordinator = null;
            }
        } else {
            readOnlyCacheMetadata = null;
            readOnlyCacheAccessCoordinator = null;
        }
    }

    @Nullable
    private static File validateReadOnlyCache(DocumentationRegistry documentationRegistry, File cacheDir) {
        if (!cacheDir.exists()) {
            LOGGER.warn("The read-only dependency cache is disabled because of a configuration problem:");
            LOGGER.warn("The " + READONLY_CACHE_ENV_VAR + " environment variable was set to " + cacheDir + " which doesn't exist!");
            return null;
        }
        File root = CacheLayout.ROOT.getPath(cacheDir);
        if (!root.exists()) {
            String docLink = documentationRegistry.getDocumentationFor("dependency_resolution", "sub:shared-readonly-cache");
            LOGGER.warn("The read-only dependency cache is disabled because of a configuration problem:");
            LOGGER.warn("Read-only cache is configured but the directory layout isn't expected. You must have a pre-populated " +
                CacheLayout.ROOT.getKey() + " directory at " + root + " . Please follow the instructions at " + docLink);
            return null;
        }
        return cacheDir;
    }

    @Override
    public ArtifactCacheMetadata getWritableCacheMetadata() {
        return writableCacheMetadata;
    }

    @Override
    public Optional<ArtifactCacheMetadata> getReadOnlyCacheMetadata() {
        return Optional.ofNullable(readOnlyCacheMetadata);
    }

    @Override
    public ArtifactCacheLockingAccessCoordinator getWritableCacheAccessCoordinator() {
        return writableCacheAccessCoordinator;
    }

    @Override
    public Optional<ArtifactCacheLockingAccessCoordinator> getReadOnlyCacheAccessCoordinator() {
        return Optional.ofNullable(readOnlyCacheAccessCoordinator);
    }

    @Override
    public List<File> getGlobalCacheRoots() {
        return readOnlyCacheMetadata == null
            ? ImmutableList.of()
            : readOnlyCacheMetadata.getGlobalCacheRoots();
    }

    @Override
    public void close() {
        writableCacheAccessCoordinator.close();
        if (readOnlyCacheAccessCoordinator != null) {
            readOnlyCacheAccessCoordinator.close();
        }
    }

    public interface WritableArtifactCacheLockingParameters {
        FileAccessTimeJournal getFileAccessTimeJournal();

        UsedGradleVersions getUsedGradleVersions();
    }

    private static class LateInitWritableArtifactCacheLockingAccessCoordinator implements ArtifactCacheLockingAccessCoordinator, Closeable {
        private final Factory<WritableArtifactCacheLockingAccessCoordinator> factory;
        private volatile WritableArtifactCacheLockingAccessCoordinator delegate;

        private LateInitWritableArtifactCacheLockingAccessCoordinator(Factory<WritableArtifactCacheLockingAccessCoordinator> factory) {
            this.factory = factory;
        }

        private WritableArtifactCacheLockingAccessCoordinator getDelegate() {
            if (delegate == null) {
                synchronized (factory) {
                    if (delegate == null) {
                        delegate = factory.create();
                    }
                }
            }
            return delegate;
        }

        @Override
        public void close() {
            if (delegate != null) {
                delegate.close();
            }
        }

        @Override
        public <T> T withFileLock(Factory<? extends T> action) {
            return getDelegate().withFileLock(action);
        }

        @Override
        public void withFileLock(Runnable action) {
            getDelegate().withFileLock(action);
        }

        @Override
        public <T> T useCache(Factory<? extends T> action) {
            return getDelegate().useCache(action);
        }

        @Override
        public void useCache(Runnable action) {
            getDelegate().useCache(action);
        }

        @Override
        public <K, V> IndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
            return getDelegate().createCache(cacheName, keySerializer, valueSerializer);
        }
    }
}
