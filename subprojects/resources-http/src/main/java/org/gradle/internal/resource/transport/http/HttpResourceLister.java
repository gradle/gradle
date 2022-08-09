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

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.transfer.ExternalResourceLister;

import java.util.List;

public class HttpResourceLister implements ExternalResourceLister {
    private final HttpResourceAccessor accessor;

    public HttpResourceLister(HttpResourceAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public List<String> list(final ExternalResourceName directory) {
        return accessor.withContent(directory, true, (inputStream, metaData) -> {
            String contentType = metaData.getContentType();
            ApacheDirectoryListingParser directoryListingParser = new ApacheDirectoryListingParser();
            try {
                return directoryListingParser.parse(directory.getUri(), inputStream, contentType);
            } catch (Exception e) {
                throw new ResourceException(directory.getUri(), String.format("Unable to parse HTTP directory listing for '%s'.", directory.getUri()), e);
            }
        });
    }
}
