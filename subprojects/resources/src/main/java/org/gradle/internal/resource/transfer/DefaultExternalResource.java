/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.internal.resource.AbstractExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class DefaultExternalResource extends AbstractExternalResource {
    private final URI uri;
    private final ExternalResourceReadResponse response;

    public DefaultExternalResource(URI uri, ExternalResourceReadResponse response) {
        this.uri = uri;
        this.response = response;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        return response.getMetaData();
    }

    @Override
    public boolean isLocal() {
        return response.isLocal();
    }

    @Override
    protected InputStream openStream() throws IOException {
        return response.openStream();
    }

    @Override
    public void close() {
        try {
            response.close();
        } catch (IOException e) {
            throw new ResourceException(uri, String.format("Could not close resource '%s'.", uri), e);
        }
    }
}
