/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.resources.ResourceException;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceReadResult;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.cached.CachedExternalResource;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaDataCompare;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DefaultCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheAwareExternalResourceAccessor.class);

    private final ExternalResourceRepository delegate;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final BuildCommencedTimeProvider timeProvider;
    private final TemporaryFileProvider temporaryFileProvider;
    private final CacheLockingManager cacheLockingManager;
    private final ExternalResourceCachePolicy externalResourceCachePolicy;
    private final ProducerGuard<ExternalResourceName> producerGuard;
    private final FileResourceRepository fileResourceRepository;

    public DefaultCacheAwareExternalResourceAccessor(ExternalResourceRepository delegate, CachedExternalResourceIndex<String> cachedExternalResourceIndex, BuildCommencedTimeProvider timeProvider, TemporaryFileProvider temporaryFileProvider, CacheLockingManager cacheLockingManager, ExternalResourceCachePolicy externalResourceCachePolicy, ProducerGuard<ExternalResourceName> producerGuard, FileResourceRepository fileResourceRepository) {
        this.delegate = delegate;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.externalResourceCachePolicy = externalResourceCachePolicy;
        this.producerGuard = producerGuard;
        this.fileResourceRepository = fileResourceRepository;
    }

    @Nullable
    @Override
    public LocallyAvailableExternalResource getResource(final ExternalResourceName location, @Nullable String baseName, final ResourceFileStore fileStore, @Nullable final LocallyAvailableResourceCandidates additionalCandidates) throws IOException {
        return producerGuard.guardByKey(location, new Factory<LocallyAvailableExternalResource>() {
            @Override
            public LocallyAvailableExternalResource create() {
                LOGGER.debug("Constructing external resource: {}", location);
                CachedExternalResource cached = cachedExternalResourceIndex.lookup(location.toString());

                // If we have no caching options, just get the thing directly
                if (cached == null && (additionalCandidates == null || additionalCandidates.isNone())) {
                    return copyToCache(location, fileStore, delegate.withProgressLogging().resource(location));
                }

                // We might be able to use a cached/locally available version
                if (cached != null && !externalResourceCachePolicy.mustRefreshExternalResource(getAgeMillis(timeProvider, cached))) {
                    return fileResourceRepository.resource(cached.getCachedFile(), location.getUri(), cached.getExternalResourceMetaData());
                }

                // We have a cached version, but it might be out of date, so we tell the upstreams to revalidate too
                final boolean revalidate = true;

                // Get the metadata first to see if it's there
                final ExternalResourceMetaData remoteMetaData = delegate.resource(location, revalidate).getMetaData();
                if (remoteMetaData == null) {
                    return null;
                }

                // Is the cached version still current?
                if (cached != null) {
                    boolean isUnchanged = ExternalResourceMetaDataCompare.isDefinitelyUnchanged(
                        cached.getExternalResourceMetaData(),
                        new Factory<ExternalResourceMetaData>() {
                            public ExternalResourceMetaData create() {
                                return remoteMetaData;
                            }
                        }
                    );

                    if (isUnchanged) {
                        LOGGER.info("Cached resource {} is up-to-date (lastModified: {}).", location, cached.getExternalLastModified());
                        // Update the cache entry in the index: this resets the age of the cached entry to zero
                        cachedExternalResourceIndex.store(location.toString(), cached.getCachedFile(), cached.getExternalResourceMetaData());
                        return fileResourceRepository.resource(cached.getCachedFile(), location.getUri(), cached.getExternalResourceMetaData());
                    }
                }

                // Either no cached, or it's changed. See if we can find something local with the same checksum
                boolean hasLocalCandidates = additionalCandidates != null && !additionalCandidates.isNone();
                if (hasLocalCandidates) {
                    // The “remote” may have already given us the checksum
                    HashValue remoteChecksum = remoteMetaData.getSha1();

                    if (remoteChecksum == null) {
                        remoteChecksum = getResourceSha1(location, revalidate);
                    }

                    if (remoteChecksum != null) {
                        LocallyAvailableResource local = additionalCandidates.findByHashValue(remoteChecksum);
                        if (local != null) {
                            LOGGER.info("Found locally available resource with matching checksum: [{}, {}]", location, local.getFile());
                            // TODO - should iterate over each candidate until we successfully copy into the cache
                            LocallyAvailableExternalResource resource;
                            try {
                                resource = copyCandidateToCache(location, fileStore, remoteMetaData, remoteChecksum, local);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            if (resource != null) {
                                return resource;
                            }
                        }
                    }
                }

                // All local/cached options failed, get directly
                return copyToCache(location, fileStore, delegate.withProgressLogging().resource(location, revalidate));
            }
        });
    }

    private HashValue getResourceSha1(ExternalResourceName location, boolean revalidate) {
        try {
            ExternalResourceName sha1Location = location.append(".sha1");
            ExternalResource resource = delegate.resource(sha1Location, revalidate);
            ExternalResourceReadResult<HashValue> result = resource.withContentIfPresent(new Transformer<HashValue, InputStream>() {
                @Override
                public HashValue transform(InputStream inputStream) {
                    try {
                        String sha = IOUtils.toString(inputStream, "us-ascii");
                        return HashValue.parse(sha);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
            return result == null ? null : result.getResult();
        } catch (Exception e) {
            throw new ResourceException(location.getUri(), String.format("Failed to download SHA1 for resource '%s'.", location), e);
        }
    }

    private LocallyAvailableExternalResource copyCandidateToCache(ExternalResourceName source, ResourceFileStore fileStore, ExternalResourceMetaData remoteMetaData, HashValue remoteChecksum, LocallyAvailableResource local) throws IOException {
        final File destination = temporaryFileProvider.createTemporaryFile("gradle_download", "bin");
        try {
            Files.copy(local.getFile(), destination);
            HashValue localChecksum = HashUtil.createHash(destination, "SHA1");
            if (!localChecksum.equals(remoteChecksum)) {
                return null;
            }
            return moveIntoCache(source, destination, fileStore, remoteMetaData);
        } finally {
            destination.delete();
        }
    }

    private LocallyAvailableExternalResource copyToCache(final ExternalResourceName source, final ResourceFileStore fileStore, final ExternalResource resource) {
        // Download to temporary location
        DownloadAction downloadAction = new DownloadAction(source);
        try {
            resource.withContentIfPresent(downloadAction);
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(source.getUri(), e);
        }
        if (downloadAction.metaData == null) {
            return null;
        }

        // Move into cache
        try {
            return moveIntoCache(source, downloadAction.destination, fileStore, downloadAction.metaData);
        } finally {
            downloadAction.destination.delete();
        }
    }

    private LocallyAvailableExternalResource moveIntoCache(final ExternalResourceName source, final File destination, final ResourceFileStore fileStore, final ExternalResourceMetaData metaData) {
        return cacheLockingManager.useCache(new Factory<LocallyAvailableExternalResource>() {
            public LocallyAvailableExternalResource create() {
                LocallyAvailableResource cachedResource = fileStore.moveIntoCache(destination);
                File fileInFileStore = cachedResource.getFile();
                cachedExternalResourceIndex.store(source.toString(), fileInFileStore, metaData);
                return fileResourceRepository.resource(fileInFileStore, source.getUri(), metaData);
            }
        });
    }

    public long getAgeMillis(BuildCommencedTimeProvider timeProvider, CachedExternalResource cached) {
        return timeProvider.getCurrentTime() - cached.getCachedAt();
    }

    private class DownloadAction implements ExternalResource.ContentAction<Object> {
        private final ExternalResourceName source;
        File destination;
        ExternalResourceMetaData metaData;

        DownloadAction(ExternalResourceName source) {
            this.source = source;
        }

        @Override
        public Object execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
            destination = temporaryFileProvider.createTemporaryFile("gradle_download", "bin");
            this.metaData = metaData;
            LOGGER.debug("Downloading {} to {}", source, destination);
            if (destination.getParentFile() != null) {
                GFileUtils.mkdirs(destination.getParentFile());
            }
            FileOutputStream outputStream = new FileOutputStream(destination);
            try {
                IOUtils.copyLarge(inputStream, outputStream);
            } finally {
                outputStream.close();
            }
            return null;
        }
    }
}
