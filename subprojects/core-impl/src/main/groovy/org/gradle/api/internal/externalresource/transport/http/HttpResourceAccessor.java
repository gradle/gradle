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

package org.gradle.api.internal.externalresource.transport.http;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.gradle.api.Nullable;
import org.gradle.api.internal.externalresource.*;
import org.gradle.api.internal.externalresource.cached.CachedExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceAdapter;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor;
import org.gradle.util.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HttpResourceAccessor implements ExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceAccessor.class);
    private final HttpClientHelper http;

    private final List<ExternalResource> openResources = new ArrayList<ExternalResource>();

    public HttpResourceAccessor(HttpClientHelper http) {
        this.http = http;
    }

    public ExternalResource getResource(String source) throws IOException {
        return getResource(source, null, null);
    }

    public ExternalResource getResource(String source, LocallyAvailableResourceCandidates localCandidates, @Nullable CachedExternalResource cached) throws IOException {
        abortOpenResources();
        LOGGER.debug("Constructing external resource: {}", source);

        // Do we have any artifacts in the cache with the same checksum
        boolean hasLocalCandidates = localCandidates != null && !localCandidates.isNone();
        if (hasLocalCandidates) {
            HashValue remoteChecksum = getChecksumFor(source);
            if (remoteChecksum != null) {
                LocallyAvailableResource local = localCandidates.findByHashValue(remoteChecksum);
                if (local != null) {
                    LOGGER.info("Found locally available resource with matching checksum: [{}, {}]", source, local.getOrigin());
                    return new LocallyAvailableExternalResource(source, local);
                }
            }
        }

        // Is the cached version still current
        if (cached != null) {
            if (isUnchanged(source, cached)) {
                LOGGER.info("Cached resource is up-to-date (lastModified: {}). [HTTP: {}]", cached.getExternalLastModified(), source);
                return new CachedExternalResourceAdapter(source, cached, this);
            }
        }

        HttpResponse response = http.performGet(source);
        if (response != null) {
            ExternalResource resource = new HttpResponseResource("GET", source, response) {
                @Override
                public void close() throws IOException {
                    super.close();
                    HttpResourceAccessor.this.openResources.remove(this);
                }
            };

            return recordOpenGetResource(resource);
        } else {
            return null;
        }
    }

    public ExternalResourceMetaData getMetaData(String source) {
        abortOpenResources();
        LOGGER.debug("Constructing external resource metadata: {}", source);
        return getMetaDataInternal(source);
    }

    private ExternalResource recordOpenGetResource(ExternalResource httpResource) {
        if (httpResource instanceof HttpResponseResource) {
            openResources.add(httpResource);
        }
        return httpResource;
    }

    private ExternalResourceMetaData getMetaDataInternal(String source) {
        HttpResponse response = http.performHead(source);
        return response == null ? null : new HttpResponseResource("HEAD", source, response).getMetaData();
    }

    private void abortOpenResources() {
        for (ExternalResource openResource : openResources) {
            LOGGER.warn("Forcing close on abandoned resource: " + openResource);
            try {
                openResource.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close abandoned resource", e);
            }
        }
        openResources.clear();
    }

    private boolean isUnchanged(String source, CachedExternalResource cached) {
        ExternalResourceMetaData metaData = cached.getExternalResourceMetaData();
        if (metaData != null) {
            Date cacheLastModified = metaData.getLastModified();
            long cacheContentLength = metaData.getContentLength();

            if (cacheLastModified != null && cacheContentLength > 0) {
                ExternalResourceMetaData remoteMetaData = getMetaDataInternal(source);

                if (remoteMetaData != null && remoteMetaData.getContentLength() > 0 && remoteMetaData.getLastModified() != null) {
                    return remoteMetaData.getContentLength() == cacheContentLength
                            && remoteMetaData.getLastModified().equals(cacheLastModified);
                }
            }
        }

        return false;
    }

    private HashValue getChecksumFor(String source) {
        String checksumUrl = source + ".sha1";
        return downloadSha1(checksumUrl);
    }

    private HashValue downloadSha1(String checksumUrl) {
        try {
            HttpResponse httpResponse = http.performRawGet(checksumUrl);
            if (http.wasSuccessful(httpResponse)) {
                String checksumValue = EntityUtils.toString(httpResponse.getEntity());
                return HashValue.parse(checksumValue);
            }
            if (!http.wasMissing(httpResponse)) {
                LOGGER.info("Request for checksum at {} failed: {}", checksumUrl, httpResponse.getStatusLine());
            }
            return null;
        } catch (Exception e) {
            LOGGER.warn("Checksum missing at {} due to: {}", checksumUrl, e.getMessage());
            return null;
        }
    }

}
