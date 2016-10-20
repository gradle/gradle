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
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.cached.CachedExternalResource;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.local.*;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaDataCompare;
import org.gradle.internal.resource.transport.ExternalResourceRepository;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class DefaultCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheAwareExternalResourceAccessor.class);

    private final ExternalResourceRepository delegate;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final BuildCommencedTimeProvider timeProvider;
    private final TemporaryFileProvider temporaryFileProvider;
    private final CacheLockingManager cacheLockingManager;
    private final ExternalResourceCachePolicy externalResourceCachePolicy = new DefaultExternalResourceCachePolicy();

    public DefaultCacheAwareExternalResourceAccessor(ExternalResourceRepository delegate, CachedExternalResourceIndex<String> cachedExternalResourceIndex, BuildCommencedTimeProvider timeProvider, TemporaryFileProvider temporaryFileProvider, CacheLockingManager cacheLockingManager) {
        this.delegate = delegate;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cacheLockingManager = cacheLockingManager;
    }

    public LocallyAvailableExternalResource getResource(final URI location, final ResourceFileStore fileStore, @Nullable LocallyAvailableResourceCandidates localCandidates) throws IOException {
        LOGGER.debug("Constructing external resource: {}", location);
        CachedExternalResource cached = cachedExternalResourceIndex.lookup(location.toString());

        // If we have no caching options, just get the thing directly
        if (cached == null && (localCandidates == null || localCandidates.isNone())) {
            return copyToCache(location, fileStore, delegate.withProgressLogging().getResource(location, false));
        }

        // We might be able to use a cached/locally available version
        if (cached != null && !externalResourceCachePolicy.mustRefreshExternalResource(getAgeMillis(timeProvider, cached))) {
            return new DefaultLocallyAvailableExternalResource(location, new DefaultLocallyAvailableResource(cached.getCachedFile()), cached.getExternalResourceMetaData());
        }

        // We have a cached version, but it might be out of date, so we tell the upstreams to revalidate too
        final boolean revalidate = true;

        // Get the metadata first to see if it's there
        final ExternalResourceMetaData remoteMetaData = delegate.getResourceMetaData(location, revalidate);
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
                // TODO - update the index with the new remote meta-data
                return new DefaultLocallyAvailableExternalResource(location, new DefaultLocallyAvailableResource(cached.getCachedFile()), cached.getExternalResourceMetaData());
            }
        }

        // Either no cached, or it's changed. See if we can find something local with the same checksum
        boolean hasLocalCandidates = localCandidates != null && !localCandidates.isNone();
        if (hasLocalCandidates) {
            // The “remote” may have already given us the checksum
            HashValue remoteChecksum = remoteMetaData.getSha1();

            if (remoteChecksum == null) {
                remoteChecksum = getResourceSha1(location, revalidate);
            }

            if (remoteChecksum != null) {
                LocallyAvailableResource local = localCandidates.findByHashValue(remoteChecksum);
                if (local != null) {
                    LOGGER.info("Found locally available resource with matching checksum: [{}, {}]", location, local.getFile());
                    // TODO - should iterate over each candidate until we successfully copy into the cache
                    LocallyAvailableExternalResource resource = copyCandidateToCache(location, fileStore, remoteMetaData, remoteChecksum, local);
                    if (resource != null) {
                        return resource;
                    }
                }
            }
        }

        // All local/cached options failed, get directly
        return copyToCache(location, fileStore, delegate.withProgressLogging().getResource(location, revalidate));
    }

    private HashValue getResourceSha1(URI location, boolean revalidate) {
        try {
            URI sha1Location = new URI(location.toASCIIString() + ".sha1");
            ExternalResource resource = delegate.getResource(sha1Location, revalidate);
            if (resource == null) {
                return null;
            }
            try {
                return resource.withContent(new Transformer<HashValue, InputStream>() {
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
            } finally {
                resource.close();
            }
        } catch (Exception e) {
            throw new ResourceException(location, String.format("Failed to download SHA1 for resource '%s'.", location), e);
        }
    }

    private LocallyAvailableExternalResource copyCandidateToCache(URI source, ResourceFileStore fileStore, ExternalResourceMetaData remoteMetaData, HashValue remoteChecksum, LocallyAvailableResource local) throws IOException {
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

    private LocallyAvailableExternalResource copyToCache(URI source, ResourceFileStore fileStore, ExternalResource resource) {
        if (resource == null) {
            return null;
        }

        final File destination = temporaryFileProvider.createTemporaryFile("gradle_download", "bin");
        try {
            final DownloadToFileAction downloadAction = new DownloadToFileAction(destination);
            try {
                try {
                    LOGGER.debug("Downloading {} to {}", source, destination);
                    if (destination.getParentFile() != null) {
                        GFileUtils.mkdirs(destination.getParentFile());
                    }
                    resource.withContent(downloadAction);
                } finally {
                    resource.close();
                }
            } catch (Exception e) {
                throw ResourceExceptions.getFailed(source, e);
            }
            return moveIntoCache(source, destination, fileStore, downloadAction.metaData);
        } finally {
            destination.delete();
        }
    }

    private LocallyAvailableExternalResource moveIntoCache(final URI source, final File destination, final ResourceFileStore fileStore, final ExternalResourceMetaData metaData) {
        return cacheLockingManager.useCache("Store " + source, new Factory<LocallyAvailableExternalResource>() {
            public LocallyAvailableExternalResource create() {
                LocallyAvailableResource cachedResource = fileStore.moveIntoCache(destination);
                File fileInFileStore = cachedResource.getFile();
                cachedExternalResourceIndex.store(source.toString(), fileInFileStore, metaData);
                return new DefaultLocallyAvailableExternalResource(source, cachedResource, metaData);
            }
        });
    }

    public long getAgeMillis(BuildCommencedTimeProvider timeProvider, CachedExternalResource cached) {
        return timeProvider.getCurrentTime() - cached.getCachedAt();
    }

    private static class DownloadToFileAction implements ExternalResource.ContentAction<Object> {
        private final File destination;
        private ExternalResourceMetaData metaData;

        public DownloadToFileAction(File destination) {
            this.destination = destination;
        }

        @Override
        public Object execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
            this.metaData = metaData;
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
