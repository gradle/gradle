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

import org.gradle.api.Transformer;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceLister;
import org.gradle.api.internal.resource.ResourceException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class HttpResourceLister implements ExternalResourceLister {
    private HttpResourceAccessor accessor;

    public HttpResourceLister(HttpResourceAccessor accessor) {
        this.accessor = accessor;
    }

    public List<String> list(String parent) throws IOException {
        final URI baseURI;
        try {
            baseURI = new URI(parent);
        } catch (URISyntaxException ex) {
            throw new ResourceException(String.format("Unable to create URI from string '%s' ", parent), ex);
        }
        final HttpResponseResource resource = accessor.getResource(baseURI.toString());
        if (resource == null) {
            return null;
        }
        try {
            return resource.withContent(new Transformer<List<String>, InputStream>() {
                public List<String> transform(InputStream inputStream) {
                    String contentType = resource.getContentType();
                    ApacheDirectoryListingParser directoryListingParser = new ApacheDirectoryListingParser();
                    try {
                        List<URI> uris = directoryListingParser.parse(baseURI, inputStream, contentType);
                        return convertToStringList(uris);
                    } catch (Exception e) {
                        throw new ResourceException("Unable to parse Http directory listing", e);
                    }
                }
            });
        } finally {
            resource.close();
        }
    }

    private List<String> convertToStringList(List<URI> uris) {
        List<String> ret = new ArrayList<String>(uris.size());
        for (URI url : uris) {
            ret.add(url.toString());
        }
        return ret;
    }
}
