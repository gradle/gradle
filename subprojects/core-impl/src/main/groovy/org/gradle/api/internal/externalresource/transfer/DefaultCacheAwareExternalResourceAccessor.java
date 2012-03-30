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
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceAdapter;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaDataCompare;
import org.gradle.internal.Factory;
import org.gradle.util.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DefaultCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheAwareExternalResourceAccessor.class);

    private final ExternalResourceAccessor delegate;

    public DefaultCacheAwareExternalResourceAccessor(ExternalResourceAccessor delegate) {
        this.delegate = delegate;
    }

    public ExternalResource getResource(final String source, @Nullable LocallyAvailableResourceCandidates localCandidates, @Nullable CachedExternalResource cached) throws IOException {
        LOGGER.debug("Constructing external resource: {}", source);

        // Do we have any artifacts in the cache with the same checksum
        boolean hasLocalCandidates = localCandidates != null && !localCandidates.isNone();
        if (hasLocalCandidates) {
            HashValue remoteChecksum = delegate.getResourceSha1(source);
            if (remoteChecksum != null) {
                LocallyAvailableResource local = localCandidates.findByHashValue(remoteChecksum);
                if (local != null) {
                    LOGGER.info("Found locally available resource with matching checksum: [{}, {}]", source, local.getFile());
                    return new LocallyAvailableExternalResource(source, local);
                }
            }
        }

        // Is the cached version still current
        if (cached != null) {
            boolean isUnchanged = ExternalResourceMetaDataCompare.isDefinitelyUnchanged(
                    cached.getExternalResourceMetaData(),
                    new Factory<ExternalResourceMetaData>() {
                        public ExternalResourceMetaData create() {
                            try {
                                return delegate.getMetaData(source);
                            } catch (IOException e) {
                                return null;
                            }
                        }
                    }
            );

            if (isUnchanged) {
                LOGGER.info("Cached resource is up-to-date (lastModified: {}). [HTTP: {}]", cached.getExternalLastModified(), source);
                return new CachedExternalResourceAdapter(source, cached, delegate);
            }
        }

        return delegate.getResource(source);
    }

}
