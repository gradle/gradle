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

import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceLister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class HttpResourceLister implements ExternalResourceLister {
    private HttpResourceAccessor accessor;
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceLister.class);

    public HttpResourceLister(HttpResourceAccessor accessor) {
        this.accessor = accessor;
    }

    public List<String> list(String parent) throws IOException {
        URI baseURI;
        try {
            baseURI = addTrailingSlashes(new URI(parent));
        } catch (URISyntaxException ex) {
            throw new IOException(String.format("Unable to create URI from String '%s' ", parent), ex);
        }
        final ExternalResource resource = accessor.getResource(baseURI.toString());
        if (resource == null) {
            return null;
        }
        byte[] resourceContent = loadResourceContent(resource);
        String contentType = getContentType(resource);
        ApacheDirectoryListingParser directoryListingParser = new ApacheDirectoryListingParser(baseURI);
        try {
            List<URI> uris = directoryListingParser.parse(resourceContent, contentType);
            return convertToStringList(uris);
        } catch (Exception e) {
            throw new IOException("Unable to parse Http directory listing", e);
        }
    }

    private List<String> convertToStringList(List<URI> uris) {
        List<String> ret = new ArrayList<String>(uris.size());
        for (URI url : uris) {
            ret.add(url.toString());
        }
        return ret;
    }

    private String getContentType(ExternalResource resource) {
        if (resource instanceof HttpResponseResource) {
            return ((HttpResponseResource) resource).getContentType();
        }
        return null;
    }

    byte[] loadResourceContent(ExternalResource resource) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            resource.writeTo(outputStream, new CopyProgressListenerAdapter());
            return outputStream.toByteArray();
        } finally {
            try {
                outputStream.close();
            } finally {
                resource.close();
            }
        }
    }

    URI addTrailingSlashes(URI uri) throws IOException, URISyntaxException {
        if (!uri.getPath().endsWith("/") && !uri.getPath().endsWith(".html")) {
            uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath() + "/", uri.getQuery(), uri.getFragment());
        }
        return uri;
    }
}
