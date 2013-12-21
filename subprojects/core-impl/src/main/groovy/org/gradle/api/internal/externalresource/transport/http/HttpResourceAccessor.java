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
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor;
import org.gradle.internal.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HttpResourceAccessor implements ExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceAccessor.class);
    private final HttpClientHelper http;

    private final List<ExternalResource> openResources = new ArrayList<ExternalResource>();

    public HttpResourceAccessor(HttpClientHelper http) {
        this.http = http;
    }

    public HttpResponseResource getResource(String location) throws IOException {
        abortOpenResources();
        LOGGER.debug("Constructing external resource: {}", location);
        HttpResponse response = http.performGet(location);
        if (response != null) {
            HttpResponseResource resource = new HttpResponseResource("GET", location, response) {
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

    public ExternalResourceMetaData getMetaData(String location) {
        abortOpenResources();
        LOGGER.debug("Constructing external resource metadata: {}", location);
        HttpResponse response = http.performHead(location);
        return response == null ? null : new HttpResponseResource("HEAD", location, response).getMetaData();
    }

    private HttpResponseResource recordOpenGetResource(HttpResponseResource httpResource) {
        openResources.add(httpResource);
        return httpResource;
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

    public HashValue getResourceSha1(String location) {
        String checksumUrl = location + ".sha1";
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
