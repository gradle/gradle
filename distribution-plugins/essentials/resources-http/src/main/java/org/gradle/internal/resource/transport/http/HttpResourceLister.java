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
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.transfer.ExternalResourceLister;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public class HttpResourceLister implements ExternalResourceLister {
    private HttpResourceAccessor accessor;

    public HttpResourceLister(HttpResourceAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public List<String> list(final URI directory) {
        final ExternalResourceReadResponse response = accessor.openResource(directory, true);
        if (response == null) {
            return null;
        }
        try {
            try {
                String contentType = response.getMetaData().getContentType();
                ApacheDirectoryListingParser directoryListingParser = new ApacheDirectoryListingParser();
                InputStream inputStream = response.openStream();
                try {
                    return directoryListingParser.parse(directory, inputStream, contentType);
                } catch (Exception e) {
                    throw new ResourceException(directory, String.format("Unable to parse HTTP directory listing for '%s'.", directory), e);
                }
            } finally {
                response.close();
            }
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(directory, e);
        }
    }
}
