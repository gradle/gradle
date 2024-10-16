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

package org.gradle.internal.resource.transport.http;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.IoActions;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.gradle.internal.resource.transfer.UrlExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

public class HttpResourceAccessor extends AbstractExternalResourceAccessor implements ExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceAccessor.class);
    private final HttpClientHelper http;

    private int chunkSize = 8192;// 8 kb

    public HttpResourceAccessor(HttpClientHelper http) {
        this.http = http;
    }

    @Override
    @Nullable
    public ExternalResourceReadResponse openResource(final ExternalResourceName location, boolean revalidate, @Nullable File partPosition) {
        return onOpenResource(location.getUri(), revalidate, partPosition, http::performGet);
    }

    /**
     * Same as #getResource except that it always gives access to the response body,
     * irrespective of the returned HTTP status code. Never returns {@code null}.
     */
    public ExternalResourceReadResponse getRawResource(final URI uri, boolean revalidate) {
        return onOpenResource(uri, revalidate, null, http::performRawGet);
    }

    private ExternalResourceReadResponse onOpenResource(final URI location, boolean revalidate, @Nullable File partPosition, @Nonnull HttpClientResponseProvider provider) {
        String uri = location.toString();
        LOGGER.debug("Constructing external resource: {}", location);

        if (partPosition == null) {
            // cache is disabled
            return wrapResponse(location, provider.get(uri, revalidate, null, null));
        }

        try {
            FileUtils.forceMkdir(partPosition.getParentFile());
        } catch (IOException e) {
            throw new ResourceException(location, "Unable to create cache directory", e);
        }

        long skip = partPosition.isFile() && partPosition.exists() ? partPosition.length() : 0; // read file size from partially downloaded file
        LOGGER.debug("Downloading partial content from bytes {}", skip);
        HttpClientResponse response = provider.get(uri, revalidate, skip, null);
        int code = response.getStatusLine().getStatusCode();
        if (code == HttpStatus.SC_OK) { // server does not support range request
            return wrapResponse(location, response);
        } else if (code == HttpStatus.SC_PARTIAL_CONTENT) { // server support range request
            try {
                IOUtils.copy(response.getContent(), new FileOutputStream(partPosition, true), chunkSize);
            } catch (IOException e) {
                throw new ResourceException(location, String.format("Unable to save partial content from bytes %d", skip), e);
            }
        } else {
            throw new ResourceException(location, String.format("Unexpected response code %d when fetching bytes from %d", code, skip));
        }
        return new UrlExternalResource().openResource(new ExternalResourceName(partPosition.toURI()), revalidate, partPosition);
    }

    @Override
    public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) {
        String uri = location.getUri().toString();
        LOGGER.debug("Constructing external resource metadata: {}", location);
        HttpClientResponse response = http.performHead(uri, revalidate);

        if (response == null || response.wasMissing()) {
            return null;
        }

        HttpResponseResource resource = new HttpResponseResource("HEAD", location.getUri(), response);
        try {
            return resource.getMetaData();
        } finally {
            IoActions.closeQuietly(resource);
        }
    }

    private HttpResponseResource wrapResponse(URI uri, HttpClientResponse response) {
        return new HttpResponseResource("GET", uri, response);
    }

    @VisibleForTesting
    void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    private interface HttpClientResponseProvider {
        @Nonnull
        HttpClientResponse get(final String location, boolean revalidate, @Nullable Long rangeStart, @Nullable Long rangeEnd);
    }
}
