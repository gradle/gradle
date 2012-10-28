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

import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.repository.TransferListener;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.cachemanager.DownloadingRepositoryCacheManager;
import org.gradle.api.internal.artifacts.repositories.cachemanager.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.transport.file.FileTransport;
import org.gradle.api.internal.externalresource.transport.http.HttpTransport;
import org.gradle.api.internal.filestore.FileStore;
import org.gradle.logging.ProgressLoggerFactory;

public class RepositoryTransportFactory {
    private final TransferListener transferListener;
    private final RepositoryCacheManager downloadingCacheManager;
    private final RepositoryCacheManager localCacheManager;
    private final ProgressLoggerFactory progressLoggerFactory;

    public RepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory,
                                      FileStore<ArtifactRevisionId> fileStore, CachedExternalResourceIndex<String> byUrlCachedExternalResourceIndex) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.transferListener = new ProgressLoggingTransferListener(progressLoggerFactory, RepositoryTransport.class);
        this.downloadingCacheManager = new DownloadingRepositoryCacheManager("downloading", fileStore, byUrlCachedExternalResourceIndex);
        this.localCacheManager = new LocalFileRepositoryCacheManager("local");
    }

    public RepositoryTransport createHttpTransport(String name, PasswordCredentials credentials) {
        return new HttpTransport(name, credentials, downloadingCacheManager, progressLoggerFactory);
    }

    public RepositoryTransport createFileTransport(String name) {
        return new FileTransport(name, localCacheManager);
    }

    public void attachListener(Repository repository) {
        if (!repository.hasTransferListener(transferListener)) {
            repository.addTransferListener(transferListener);
        }
    }

    public RepositoryCacheManager getDownloadingCacheManager() {
        return downloadingCacheManager;
    }

    public RepositoryCacheManager getLocalCacheManager() {
        return localCacheManager;
    }
}
