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

package org.gradle.api.internal.externalresource.transfer;

import org.gradle.api.Nullable;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceAdapter;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaDataCompare;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.util.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DefaultCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheAwareExternalResourceAccessor.class);

    private final ExternalResourceAccessor delegate;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final BuildCommencedTimeProvider timeProvider;
    private final ExternalResourceCachePolicy externalResourceCachePolicy = new DefaultExternalResourceCachePolicy();

    public DefaultCacheAwareExternalResourceAccessor(ExternalResourceAccessor delegate, CachedExternalResourceIndex<String> cachedExternalResourceIndex, BuildCommencedTimeProvider timeProvider) {
        this.delegate = delegate;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
    }

    public ExternalResource getResource(final String location, @Nullable LocallyAvailableResourceCandidates localCandidates) throws IOException {
        LOGGER.debug("Constructing external resource: {}", location);
        CachedExternalResource cached = cachedExternalResourceIndex.lookup(location);

        // If we have no caching options, just get the thing directly
        if (cached == null && (localCandidates == null || localCandidates.isNone())) {
            return delegate.getResource(location);
        }

        // We might be able to use a cached/locally available version
        if (cached != null && !externalResourceCachePolicy.mustRefreshExternalResource(getAgeMillis(timeProvider, cached))) {
            return new CachedExternalResourceAdapter(location, cached, delegate, cached.getExternalResourceMetaData());
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
                // TODO - should we use the remote metadata? It may be “better”
                return new CachedExternalResourceAdapter(location, cached, delegate, remoteMetaData);
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
                    return new DefaultLocallyAvailableExternalResource(location, local, remoteMetaData);
                }
            }
        }

        // All local/cached options failed, get directly
        return delegate.getResource(location);
    }

    public long getAgeMillis(BuildCommencedTimeProvider timeProvider, CachedExternalResource cached) {
        return timeProvider.getCurrentTime() - cached.getCachedAt();
    }
}
