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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HttpResourceLister implements ExternalResourceLister {
    private HttpResourceAccessor accessor;

    public HttpResourceLister(HttpResourceAccessor accessor) {
        this.accessor = accessor;
    }

    public List<String> list(String parent) throws IOException {
        final URL baseUrl = addTrailingSlashes(new URL(parent));
        final ExternalResource resource = accessor.getResource(baseUrl.toString());
        if (resource == null) {
            return null;
        }
        byte[] resourceContent = loadResourceContent(resource);
        String encoding = getResourceEncoding(resource);
        ApacheDirectoryListingParser directoryListingParser = new ApacheDirectoryListingParser(baseUrl);
        List<URL> urls = directoryListingParser.parse(resourceContent, encoding);

        return convertToStringMap(urls);
    }

    private List<String> convertToStringMap(List<URL> urls) {
        List<String> ret = new ArrayList<String>(urls.size());
        for (URL url : urls) {
            ret.add(url.toExternalForm());
        }
        return ret;
    }

    private String getResourceEncoding(ExternalResource resource) {
        if (resource instanceof HttpResponseResource) {
            return ((HttpResponseResource) resource).getContentEncoding();
        }
        return null;
    }

    byte[] loadResourceContent(ExternalResource resource) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            resource.writeTo(outputStream, new CopyProgressListenerAdapter());
            return outputStream.toByteArray();
        } finally {
            try{
                outputStream.close();
            }finally {
                resource.close();
            }
        }
    }

    URL addTrailingSlashes(URL url) throws IOException {
        // add trailing slash for relative urls
        if (!url.getPath().endsWith("/") && !url.getPath().endsWith(".html")) {
            url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath() + "/");
        }
        return url;
    }
}
