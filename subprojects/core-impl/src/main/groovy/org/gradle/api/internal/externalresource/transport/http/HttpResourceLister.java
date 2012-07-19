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
        final URL inputUrl = new URL(parent);
        String htmlText = loadResourceContent(new URL(parent));
        ApacheDirectoryListingParser directoryListingParser = new ApacheDirectoryListingParser(inputUrl);
        List<URL> urls = directoryListingParser.parse(htmlText);
        List<String> ret = new ArrayList<String>(urls.size());
        for (URL url : urls) {
            ret.add(url.toExternalForm());
        }
        return ret;
    }

    String loadResourceContent(URL url) throws IOException {
        // add trailing slash for relative urls
        if (!url.getPath().endsWith("/") && !url.getPath().endsWith(".html")) {
            url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath() + "/");
        }

        final ExternalResource resource = accessor.getResource(url.toString());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resource.writeTo(outputStream, new CopyProgressListenerAdapter());
        outputStream.close();
        resource.close();
        return outputStream.toString();
    }
}
