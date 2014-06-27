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

import org.gradle.api.Nullable;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.internal.resource.DefaultLocallyAvailableExternalResource;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.cached.CachedExternalResource;
import org.gradle.internal.resource.cached.CachedExternalResourceAdapter;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaDataCompare;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.resource.ResourceException;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class DefaultCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheAwareExternalResourceAccessor.class);

    private final ExternalResourceAccessor delegate;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final BuildCommencedTimeProvider timeProvider;
    private final TemporaryFileProvider temporaryFileProvider;
    private final CacheLockingManager cacheLockingManager;
    private final ExternalResourceCachePolicy externalResourceCachePolicy = new DefaultExternalResourceCachePolicy();

    public DefaultCacheAwareExternalResourceAccessor(ExternalResourceAccessor delegate, CachedExternalResourceIndex<String> cachedExternalResourceIndex, BuildCommencedTimeProvider timeProvider, TemporaryFileProvider temporaryFileProvider, CacheLockingManager cacheLockingManager) {
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
            return copyToCache(location, fileStore, delegate.getResource(location));
        }

        // We might be able to use a cached/locally available version
        if (cached != null && !externalResourceCachePolicy.mustRefreshExternalResource(getAgeMillis(timeProvider, cached))) {
            return new DefaultLocallyAvailableExternalResource(location, new DefaultLocallyAvailableResource(cached.getCachedFile()), cached.getExternalResourceMetaData());
        }

        // Get the metadata first to see if it's there
        final ExternalResourceMetaData remoteMetaData = delegate.getMetaData(location);
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
                LOGGER.info("Cached resource is up-to-date (lastModified: {}). [HTTP: {}]", cached.getExternalLastModified(), location);
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
                remoteChecksum = delegate.getResourceSha1(location);
            }

            if (remoteChecksum != null) {
                LocallyAvailableResource local = localCandidates.findByHashValue(remoteChecksum);
                if (local != null) {
                    LOGGER.info("Found locally available resource with matching checksum: [{}, {}]", location, local.getFile());
                    // TODO - should iterate over each candidate until we successfully copy into the cache
                    return copyToCache(location, fileStore, new CachedExternalResourceAdapter(location, local, delegate, remoteMetaData, remoteChecksum));
                }
            }
        }

        // All local/cached options failed, get directly
        return copyToCache(location, fileStore, delegate.getResource(location));
    }

    private LocallyAvailableExternalResource copyToCache(final URI source, final ResourceFileStore fileStore, final ExternalResource resource) {
        if (resource == null) {
            return null;
        }

        final File destination = temporaryFileProvider.createTemporaryFile("gradle_download", "bin");
        try {
            try {
                try {
                    LOGGER.debug("Downloading {} to {}", resource.getName(), destination);
                    if (destination.getParentFile() != null) {
                        GFileUtils.mkdirs(destination.getParentFile());
                    }
                    resource.writeTo(destination);
                } finally {
                    resource.close();
                }
            } catch (IOException e) {
                throw new ResourceException(String.format("Failed to download resource '%s'.", resource.getName()), e);
            }
            return cacheLockingManager.useCache(String.format("Store %s", resource.getName()), new Factory<LocallyAvailableExternalResource>() {
                public LocallyAvailableExternalResource create() {
                    LocallyAvailableResource cachedResource = fileStore.moveIntoCache(destination);
                    File fileInFileStore = cachedResource.getFile();
                    ExternalResourceMetaData metaData = resource.getMetaData();
                    cachedExternalResourceIndex.store(source.toString(), fileInFileStore, metaData);
                    return new DefaultLocallyAvailableExternalResource(source, cachedResource, metaData);
                }
            });
        } finally {
            destination.delete();
        }
    }

    public long getAgeMillis(BuildCommencedTimeProvider timeProvider, CachedExternalResource cached) {
        return timeProvider.getCurrentTime() - cached.getCachedAt();
    }
}
