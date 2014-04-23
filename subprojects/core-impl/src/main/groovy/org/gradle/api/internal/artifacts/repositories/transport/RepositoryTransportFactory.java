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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.transport.file.FileTransport;
import org.gradle.api.internal.externalresource.transport.http.HttpTransport;
import org.gradle.api.internal.externalresource.transport.sftp.SftpClientFactory;
import org.gradle.api.internal.externalresource.transport.sftp.SftpTransport;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.WrapUtil;

import java.util.HashSet;
import java.util.Set;

public class RepositoryTransportFactory {
    private final RepositoryArtifactCache downloadingCacheManager;
    private final TemporaryFileProvider temporaryFileProvider;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final RepositoryArtifactCache localCacheManager;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildCommencedTimeProvider timeProvider;
    private final SftpClientFactory sftpClientFactory;


    public RepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory,
                                      RepositoryArtifactCache localCacheManager,
                                      RepositoryArtifactCache downloadingCacheManager,
                                      TemporaryFileProvider temporaryFileProvider,
                                      CachedExternalResourceIndex<String> cachedExternalResourceIndex,
                                      BuildCommencedTimeProvider timeProvider,
                                      SftpClientFactory sftpClientFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.localCacheManager = localCacheManager;
        this.downloadingCacheManager = downloadingCacheManager;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
        this.sftpClientFactory = sftpClientFactory;
    }

    private RepositoryTransport createHttpTransport(String name, PasswordCredentials credentials) {
        return new HttpTransport(name, credentials, downloadingCacheManager, progressLoggerFactory, temporaryFileProvider, cachedExternalResourceIndex, timeProvider);
    }

    private RepositoryTransport createFileTransport(String name) {
        return new FileTransport(name, localCacheManager, temporaryFileProvider);
    }

    private RepositoryTransport createSftpTransport(String name, PasswordCredentials credentials) {
        return new SftpTransport(name, credentials, downloadingCacheManager, progressLoggerFactory, temporaryFileProvider, cachedExternalResourceIndex, timeProvider, sftpClientFactory);
    }

    public RepositoryTransport createTransport(String scheme, String name, PasswordCredentials credentials) {
        Set<String> schemes = new HashSet<String>();
        schemes.add(scheme);
        return createTransport(schemes, name, credentials);
    }

    public RepositoryTransport createTransport(Set<String> schemes, String name, PasswordCredentials credentials) {
        if (!WrapUtil.toSet("http", "https", "file", "sftp").containsAll(schemes)) {
            throw new InvalidUserDataException("You may only specify 'file', 'http', 'https' and 'sftp' URLs for a repository.");
        }
        if (WrapUtil.toSet("http", "https").containsAll(schemes)) {
            return createHttpTransport(name, credentials);
        }
        if (WrapUtil.toSet("file").containsAll(schemes)) {
            return createFileTransport(name);
        }
        if (WrapUtil.toSet("sftp").containsAll(schemes)) {
            return createSftpTransport(name, credentials);
        }
        throw new InvalidUserDataException("You cannot mix different URL schemes for a single repository. Please declare separate repositories.");
    }
}
