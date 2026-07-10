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

import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpHeaders;
import org.gradle.internal.IoActions;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class HttpResourceAccessor extends AbstractExternalResourceAccessor implements ExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceAccessor.class);
    private static final ImmutableMap<String, String> REVALIDATE_HEADERS = ImmutableMap.of(HttpHeaders.CACHE_CONTROL, "max-age=0");

    private final HttpClient client;

    public HttpResourceAccessor(HttpClient client) {
        this.client = client;
    }

    @Override
    @Nullable
    public HttpResponseResource openResource(final ExternalResourceName location, boolean revalidate) {
        LOGGER.debug("Constructing external resource: {}", location);

        URI uri = location.getUri();
        HttpClient.Response response = client.performGet(uri, getHeaders(revalidate));
        return wrapResponse(uri, response);
    }

    @Override
    public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) {
        LOGGER.debug("Constructing external resource metadata: {}", location);

        URI uri = location.getUri();
        HttpClient.Response response = client.performHead(uri, getHeaders(revalidate));

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

    private static ImmutableMap<String, String> getHeaders(boolean revalidate) {
        return revalidate ? REVALIDATE_HEADERS : ImmutableMap.of();
    }

}
