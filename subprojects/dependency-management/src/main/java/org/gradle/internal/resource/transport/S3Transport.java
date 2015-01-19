/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.artifacts.repositories.AwsCredentials;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.DefaultCacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ProgressLoggingExternalResourceUploader;
import org.gradle.internal.resource.transport.aws.s3.S3Client;
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties;
import org.gradle.internal.resource.transport.aws.s3.S3ResourceConnector;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.BuildCommencedTimeProvider;

public class S3Transport extends AbstractRepositoryTransport {
    private final ExternalResourceRepository repository;
    private final DefaultCacheAwareExternalResourceAccessor resourceAccessor;

    public S3Transport(String name, AwsCredentials awsCredentials,
                          ProgressLoggerFactory progressLoggerFactory,
                          TemporaryFileProvider temporaryFileProvider,
                          CachedExternalResourceIndex<String> cachedExternalResourceIndex,
                          BuildCommencedTimeProvider timeProvider,
                          CacheLockingManager cacheLockingManager) {
        super(name);
        S3ResourceConnector connector = new S3ResourceConnector(new S3Client(awsCredentials, new S3ConnectionProperties()));
        ProgressLoggingExternalResourceUploader uploader = new ProgressLoggingExternalResourceUploader(connector, progressLoggerFactory);
        ProgressLoggingExternalResourceAccessor loggingAccessor = new ProgressLoggingExternalResourceAccessor(connector, progressLoggerFactory);
        resourceAccessor = new DefaultCacheAwareExternalResourceAccessor(loggingAccessor, cachedExternalResourceIndex, timeProvider, temporaryFileProvider, cacheLockingManager);
        repository = new DefaultExternalResourceRepository(name, connector, uploader, connector);
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
