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

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

public class UrlExternalResource extends AbstractExternalResourceAccessor implements ExternalResourceConnector {
    public static ExternalResource open(URL url) throws IOException {
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        UrlExternalResource connector = new UrlExternalResource();
        return new AccessorBackedExternalResource(new ExternalResourceName(uri), connector, connector, connector, false);
    }

    @Nullable
    @Override
    public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) throws ResourceException {
        try {
            URL url = location.getUri().toURL();
            URLConnection connection = url.openConnection();
            try {
                return new DefaultExternalResourceMetaData(location.getUri(), connection.getLastModified(), connection.getContentLength(), connection.getContentType(), null, null);
            } finally {
                connection.getInputStream().close();
            }
        } catch (FileNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(location.getUri(), e);
        }
    }

    @Nullable
    @Override
    public ExternalResourceReadResponse openResource(final ExternalResourceName location, boolean revalidate) throws ResourceException {
        try {
            URL url = location.getUri().toURL();
            final URLConnection connection = url.openConnection();
            final InputStream inputStream = connection.getInputStream();
            return new ExternalResourceReadResponse() {
                @Override
                public InputStream openStream() {
                    return inputStream;
                }

                @Override
                public ExternalResourceMetaData getMetaData() {
                    return new DefaultExternalResourceMetaData(location.getUri(), connection.getLastModified(), connection.getContentLength(), connection.getContentType(), null, null);
                }

                @Override
                public void close() throws IOException {
                    inputStream.close();
                }
            };
        } catch (FileNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(location.getUri(), e);
        }
    }

    @Nullable
    @Override
    public List<String> list(ExternalResourceName parent) throws ResourceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void upload(ReadableContent resource, ExternalResourceName destination) throws IOException {
        throw new UnsupportedOperationException();
    }
}
