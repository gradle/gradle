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
package org.gradle.api.internal.artifacts.repositories.transport;

import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.transport.file.FileTransport;
import org.gradle.api.internal.externalresource.transport.http.HttpTransport;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;

public class RepositoryTransportFactory {
    private final RepositoryArtifactCache downloadingCacheManager;
    private final TemporaryFileProvider temporaryFileProvider;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final RepositoryArtifactCache localCacheManager;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildCommencedTimeProvider timeProvider;

    public RepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory,
                                      RepositoryArtifactCache localCacheManager,
                                      RepositoryArtifactCache downloadingCacheManager,
                                      TemporaryFileProvider temporaryFileProvider,
                                      CachedExternalResourceIndex<String> cachedExternalResourceIndex,
                                      BuildCommencedTimeProvider timeProvider) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.localCacheManager = localCacheManager;
        this.downloadingCacheManager = downloadingCacheManager;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
    }

    public RepositoryTransport createHttpTransport(String name, PasswordCredentials credentials) {
        return new HttpTransport(name, credentials, downloadingCacheManager, progressLoggerFactory, temporaryFileProvider, cachedExternalResourceIndex, timeProvider);
    }

    public RepositoryTransport createFileTransport(String name) {
        return new FileTransport(name, localCacheManager, temporaryFileProvider);
    }
}
