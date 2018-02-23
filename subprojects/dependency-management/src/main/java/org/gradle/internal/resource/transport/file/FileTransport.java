/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.transport.file;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.DefaultCacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transport.AbstractRepositoryTransport;
import org.gradle.util.BuildCommencedTimeProvider;

import javax.annotation.Nullable;
import java.io.IOException;

public class FileTransport extends AbstractRepositoryTransport {
    private final FileResourceRepository repository;
    private final FileCacheAwareExternalResourceAccessor resourceAccessor;

    public FileTransport(String name, FileResourceRepository repository, CachedExternalResourceIndex<String> cachedExternalResourceIndex, TemporaryFileProvider temporaryFileProvider, BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager, ProducerGuard<ExternalResourceName> producerGuard) {
        super(name);
        this.repository = repository;
        ExternalResourceCachePolicy cachePolicy = new DefaultExternalResourceCachePolicy();
        resourceAccessor = new FileCacheAwareExternalResourceAccessor(new DefaultCacheAwareExternalResourceAccessor(repository, cachedExternalResourceIndex, timeProvider, temporaryFileProvider, cacheLockingManager, cachePolicy, producerGuard, repository));
    }

    public boolean isLocal() {
        return true;
    }

    public ExternalResourceRepository getRepository() {
        return repository;
    }

    public CacheAwareExternalResourceAccessor getResourceAccessor() {
        return resourceAccessor;
    }

    private class FileCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {
        private final CacheAwareExternalResourceAccessor delegate;

        FileCacheAwareExternalResourceAccessor(CacheAwareExternalResourceAccessor delegate) {
            this.delegate = delegate;
        }

        @Nullable
        @Override
        public LocallyAvailableExternalResource getResource(ExternalResourceName source, @Nullable String baseName, ResourceFileStore fileStore, @Nullable LocallyAvailableResourceCandidates additionalCandidates) throws IOException {
            LocallyAvailableExternalResource resource = repository.resource(source);
            if (!resource.getFile().exists()) {
                return null;
            }
            if (baseName == null || resource.getFile().getName().equals(baseName)) {
                // Use the origin file when it can satisfy the basename requirements
                return resource;
            }

            // Use the file from the cache when it does not
            return delegate.getResource(source, baseName, fileStore, additionalCandidates);
        }
    }
}
