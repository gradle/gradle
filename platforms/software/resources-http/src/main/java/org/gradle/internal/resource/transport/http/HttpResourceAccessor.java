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
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.IoActions;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.gradle.internal.resource.transfer.UrlExternalResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

public class HttpResourceAccessor extends AbstractExternalResourceAccessor implements ExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceAccessor.class);

    private final HttpClient client;

    private int chunkSize = 8192; // 8 kb

    public HttpResourceAccessor(HttpClient client) {
        this.client = client;
    }

    @Override
    @Nullable
    public ExternalResourceReadResponse openResource(final ExternalResourceName location, boolean revalidate, @Nullable File partPosition) {
        LOGGER.debug("Constructing external resource: {}", location);

        URI uri = location.getUri();
        if (partPosition == null) {
            return wrapResponse(uri, client.performGet(uri, getHeaders(revalidate, null)));
        }

        try {
            return openResourceWithPartialDownload(uri, revalidate, partPosition);
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(uri, e);
        }
    }

    @Nullable
    protected ExternalResourceReadResponse openResource(final ExternalResourceName location, boolean revalidate) {
        return openResource(location, revalidate, null);
    }

    private ExternalResourceReadResponse openResourceWithPartialDownload(URI location, boolean revalidate, File partPosition) throws IOException {
        File parentFile = partPosition.getParentFile();
        if (parentFile != null) {
            FileUtils.forceMkdir(parentFile);
        }

        long skip = partPosition.isFile() ? partPosition.length() : 0;
        LOGGER.debug("Downloading partial content from bytes {}", skip);

        HttpClient.Response response = client.performRawGet(location, getHeaders(revalidate, skip));
        int code = response.getStatusCode();
        if (code == HttpStatus.SC_OK) {
            return wrapResponse(location, response);
        }
        if (code == HttpStatus.SC_PARTIAL_CONTENT) {
            try (HttpClient.Response partialResponse = response;
                 FileOutputStream output = new FileOutputStream(partPosition, true)) {
                IOUtils.copyLarge(partialResponse.getContent(), output, new byte[chunkSize]);
            } catch (IOException e) {
                throw new ResourceException(location, String.format("Unable to save partial content from bytes %d", skip), e);
            }
            return new UrlExternalResource().openResource(new ExternalResourceName(partPosition.toURI()), revalidate, partPosition);
        }

        response.close();
        throw new ResourceException(location, String.format("Unexpected response code %d when fetching bytes from %d", code, skip));
    }

    @Override
    public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) {
        LOGGER.debug("Constructing external resource metadata: {}", location);

        URI uri = location.getUri();
        HttpClient.Response response = client.performHead(uri, getHeaders(revalidate, null));

        if (response.isMissing()) {
            return null;
        }

        HttpResponseResource resource = new HttpResponseResource("HEAD", uri, response);
        try {
            return resource.getMetaData();
        } finally {
            IoActions.closeQuietly(resource);
        }
    }

    private HttpResponseResource wrapResponse(URI uri, HttpClient.Response response) {
        return new HttpResponseResource("GET", uri, response);
    }

    private static ImmutableMap<String, String> getHeaders(boolean revalidate, @Nullable Long rangeStart) {
        ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();
        if (revalidate) {
            headers.put(HttpHeaders.CACHE_CONTROL, "max-age=0");
        }
        if (rangeStart != null) {
            headers.put(HttpHeaders.RANGE, String.format("bytes=%d-", rangeStart));
        }
        return headers.build();
    }

    @VisibleForTesting
    void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

}
