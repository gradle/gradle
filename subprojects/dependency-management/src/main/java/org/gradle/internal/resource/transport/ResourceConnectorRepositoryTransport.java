/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.resource.transport;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.DefaultCacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceUploader;
import org.gradle.util.BuildCommencedTimeProvider;

public class ResourceConnectorRepositoryTransport extends AbstractRepositoryTransport {
    private final ExternalResourceRepository repository;
    private final DefaultCacheAwareExternalResourceAccessor resourceAccessor;

    public ResourceConnectorRepositoryTransport(String name,
                                                ProgressLoggerFactory progressLoggerFactory,
                                                TemporaryFileProvider temporaryFileProvider,
                                                CachedExternalResourceIndex<String> cachedExternalResourceIndex,
                                                BuildCommencedTimeProvider timeProvider,
                                                CacheLockingManager cacheLockingManager,
                                                ExternalResourceConnector connector,
                                                BuildOperationExecutor buildOperationExecutor,
                                                ExternalResourceCachePolicy cachePolicy,
                                                ProducerGuard<ExternalResourceName> producerGuard,
                                                FileResourceRepository fileResourceRepository) {
        super(name);
        ProgressLoggingExternalResourceUploader loggingUploader = new ProgressLoggingExternalResourceUploader(connector, progressLoggerFactory);
        ProgressLoggingExternalResourceAccessor loggingAccessor = new ProgressLoggingExternalResourceAccessor(connector, progressLoggerFactory);
        repository = new DefaultExternalResourceRepository(name, connector, connector, connector, loggingAccessor, loggingUploader, buildOperationExecutor);
        resourceAccessor = new DefaultCacheAwareExternalResourceAccessor(repository, cachedExternalResourceIndex, timeProvider, temporaryFileProvider, cacheLockingManager, cachePolicy, producerGuard, fileResourceRepository);
    }

    public ExternalResourceRepository getRepository() {
        return repository;
    }

    public CacheAwareExternalResourceAccessor getResourceAccessor() {
        return resourceAccessor;
    }

    public boolean isLocal() {
        return false;
    }
}
