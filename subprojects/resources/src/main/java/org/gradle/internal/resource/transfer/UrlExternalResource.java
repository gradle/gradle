/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.AbstractExternalResource;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

public class UrlExternalResource extends AbstractExternalResource {
    private final URI uri;
    private final URL url;

    public static ExternalResource open(URL url) throws IOException {
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return new UrlExternalResource(uri, url);
    }

    private UrlExternalResource(URI uri, URL url) throws IOException {
        this.uri = uri;
        this.url = url;
    }

    public URI getURI() {
        return uri;
    }

    public ExternalResourceMetaData getMetaData() {
        try {
            URLConnection connection = url.openConnection();
            try {
                return new DefaultExternalResourceMetaData(uri, connection.getLastModified(), connection.getContentLength(), connection.getContentType(), null, null);
            } finally {
                connection.getInputStream().close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(uri, e);
        }
    }

    public long getContentLength() {
        return getMetaData().getContentLength();
    }

    public long getLastModified() {
        return getMetaData().getLastModified().getTime();
    }

    public InputStream openStream() throws IOException {
        try {
            return url.openConnection().getInputStream();
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
