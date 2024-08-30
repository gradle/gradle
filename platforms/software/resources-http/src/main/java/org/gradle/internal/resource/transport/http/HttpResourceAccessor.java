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
import org.apache.http.HttpHeaders;
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
import java.net.URI;

public class HttpResourceAccessor extends AbstractExternalResourceAccessor implements ExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceAccessor.class);
    private final HttpClientHelper http;

    private long rangeSize = 2 * 1024 * 1024;// 2 Mb

    public HttpResourceAccessor(HttpClientHelper http) {
        this.http = http;
    }

    @Override
    @Nullable
    public ExternalResourceReadResponse openResource(final ExternalResourceName location, boolean revalidate) {
        return onOpenResource(location.getUri(), revalidate, http::performGet);
    }

    /**
     * Same as #getResource except that it always gives access to the response body,
     * irrespective of the returned HTTP status code. Never returns {@code null}.
     */
    public ExternalResourceReadResponse getRawResource(final URI uri, boolean revalidate) {
        return onOpenResource(uri, revalidate, http::performRawGet);
    }

    private ExternalResourceReadResponse onOpenResource(final URI location, boolean revalidate, @Nonnull HttpClientResponseProvider provider) {
        String uri = location.toString();
        LOGGER.debug("Constructing external resource: {}", location);

        long skip = 0;// TODO 2024/8/30: read file size from partially downloaded file
        int round = 0;
        Long totalBytes = null;
        long receivedBytes;
        do {
            long rangeStart = skip + round * rangeSize;
            HttpClientResponse response = provider.get(uri, revalidate, rangeStart, rangeStart + rangeSize);
            int code = response.getStatusLine().getStatusCode();
            if (code == HttpStatus.SC_OK) { // server does not support range request
                return wrapResponse(location, response);
            } else if (code == HttpStatus.SC_PARTIAL_CONTENT) { // server support range request

                // example of contentRange: "bytes 0-100/5083"
                String contentRange = response.getHeader(HttpHeaders.CONTENT_RANGE);
                String[] split = contentRange.replace("bytes ", "").split("/");// ["0-100", "5083"]
                if (totalBytes == null) {
                    totalBytes = Long.parseLong(split[1]);
                }
                String[] receivedRange = split[0].split("-"); // ["0", "100"]
                receivedBytes = Long.parseLong(receivedRange[1]);
                round++;
                // TODO 2024/8/30: amend response content to the temp file
            } else {
                throw new ResourceException("Unexpected response code: " + code + " when fetching resource: " + location);
            }
        } while (receivedBytes != totalBytes - 1);
        return new UrlExternalResource().openResource(new ExternalResourceName("THE TEMP FILE PATH"), revalidate);
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
    void setRangeSize(long rangeSize) {
        this.rangeSize = rangeSize;
    }

    private interface HttpClientResponseProvider {
        @Nonnull
        HttpClientResponse get(final String location, boolean revalidate, long rangeStart, long rangeEnd);
    }
}
