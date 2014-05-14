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

import org.gradle.api.Transformer;
import org.gradle.internal.resource.transfer.ExternalResourceLister;
import org.gradle.internal.resource.ResourceException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public class HttpResourceLister implements ExternalResourceLister {
    private HttpResourceAccessor accessor;

    public HttpResourceLister(HttpResourceAccessor accessor) {
        this.accessor = accessor;
    }

    public List<String> list(final URI parent) throws IOException {
        final HttpResponseResource resource = accessor.getResource(parent);
        if (resource == null) {
            return null;
        }
        try {
            return resource.withContent(new Transformer<List<String>, InputStream>() {
                public List<String> transform(InputStream inputStream) {
                    String contentType = resource.getContentType();
                    ApacheDirectoryListingParser directoryListingParser = new ApacheDirectoryListingParser();
                    try {
                        return directoryListingParser.parse(parent, inputStream, contentType);
                    } catch (Exception e) {
                        throw new ResourceException("Unable to parse HTTP directory listing.", e);
                    }
                }
            });
        } finally {
            resource.close();
        }
    }
}
