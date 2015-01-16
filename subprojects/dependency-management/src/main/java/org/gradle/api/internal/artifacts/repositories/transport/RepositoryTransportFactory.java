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
import org.gradle.api.artifacts.repositories.AwsCredentials;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.credentials.Credentials;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.transport.aws.s3.S3TransportBuilder;
import org.gradle.internal.resource.transport.file.FileTransport;
import org.gradle.internal.resource.transport.http.HttpTransport;
import org.gradle.internal.resource.transport.sftp.SftpClientFactory;
import org.gradle.internal.resource.transport.sftp.SftpTransport;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.WrapUtil;

import java.util.HashSet;
import java.util.Set;

public class RepositoryTransportFactory {
    private static final String[] SUPPORTED_SCHEMES = new String[]{"http", "https", "file", "sftp", "s3"};
    private final TemporaryFileProvider temporaryFileProvider;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildCommencedTimeProvider timeProvider;
    private final SftpClientFactory sftpClientFactory;
    private final CacheLockingManager cacheLockingManager;

    public RepositoryTransportFactory(ProgressLoggerFactory progressLoggerFactory,
                                      TemporaryFileProvider temporaryFileProvider,
                                      CachedExternalResourceIndex<String> cachedExternalResourceIndex,
                                      BuildCommencedTimeProvider timeProvider,
                                      SftpClientFactory sftpClientFactory,
                                      CacheLockingManager cacheLockingManager) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
        this.sftpClientFactory = sftpClientFactory;
        this.cacheLockingManager = cacheLockingManager;
    }

    private RepositoryTransport createHttpTransport(String name, Credentials credentials) {
        return new HttpTransport(name, convertPasswordCredentials(credentials), progressLoggerFactory, temporaryFileProvider, cachedExternalResourceIndex, timeProvider, cacheLockingManager);
    }

    private RepositoryTransport createFileTransport(String name) {
        return new FileTransport(name);
    }

    private RepositoryTransport createSftpTransport(String name, Credentials credentials) {
        return new SftpTransport(name, convertPasswordCredentials(credentials), progressLoggerFactory, temporaryFileProvider, cachedExternalResourceIndex, timeProvider, sftpClientFactory, cacheLockingManager);
    }

    public RepositoryTransport createTransport(String scheme, String name, Credentials credentials) {
        Set<String> schemes = new HashSet<String>();
        schemes.add(scheme);
        return createTransport(schemes, name, credentials);
    }

    private org.gradle.internal.resource.PasswordCredentials convertPasswordCredentials(Credentials credentials) {
        if (!(credentials instanceof PasswordCredentials)) {
            throw new IllegalArgumentException(String.format("Credentials must be an instance of: %s", PasswordCredentials.class.getCanonicalName()));
        }
        PasswordCredentials passwordCredentials = (PasswordCredentials) credentials;
        return new org.gradle.internal.resource.PasswordCredentials(passwordCredentials.getUsername(), passwordCredentials.getPassword());
    }

    private RepositoryTransport createS3Transport(String name, Credentials credentials) {
        return new S3TransportBuilder()
                .awsCredentials((AwsCredentials) credentials)
                .cacheLockingManager(cacheLockingManager)
                .cachedExternalResourceIndex(cachedExternalResourceIndex)
                .name(name)
                .progressLoggerFactory(progressLoggerFactory)
                .temporaryFileProvider(temporaryFileProvider)
                .timeProvider(timeProvider)
                .build();
    }

    public RepositoryTransport createTransport(Set<String> schemes, String name, Credentials credentials) {
        validateSchemes(schemes);
        if (WrapUtil.toSet("http", "https").containsAll(schemes)) {
            return createHttpTransport(name, credentials);
        }
        if (WrapUtil.toSet("file").containsAll(schemes)) {
            return createFileTransport(name);
        }
        if (WrapUtil.toSet("sftp").containsAll(schemes)) {
            return createSftpTransport(name, credentials);
        }
        if (WrapUtil.toSet("s3").containsAll(schemes)) {
            return createS3Transport(name, credentials);
        }
        throw new InvalidUserDataException("You cannot mix different URL schemes for a single repository. Please declare separate repositories.");
    }

    private void validateSchemes(Set<String> schemes) {
        if (!WrapUtil.toSet(SUPPORTED_SCHEMES).containsAll(schemes)) {
            throw new InvalidUserDataException("You may only specify 'file', 'http', 'https', 'sftp' and 's3' URLs for a repository.");
        }
    }
}
