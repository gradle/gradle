/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource;

import org.gradle.api.Nullable;
import org.gradle.api.resources.ResourceException;

import java.io.File;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;

/**
 * A {@link TextResource} implementation backed by a {@link UriTextResource}. This helps hide the internal details about file caching.
 */
public class ProxyUriTextResource implements TextResource {

    private final UriTextResource resource;
    private final URI sourceUri;

    public ProxyUriTextResource(UriTextResource resource, URI sourceUri) {
        this.resource = resource;
        this.sourceUri = sourceUri;
    }

    @Override
    public ResourceLocation getLocation() {
        return new ProxyUriResourceLocation();
    }

    public File getSourceFile() {
        return null;
    }

    public URI geSourceURI() {
        return sourceUri;
    }

    @Nullable
    @Override
    public File getFile() {
        //Don't expose the file
        return null;
    }

    @Nullable
    @Override
    public Charset getCharset() {
        return resource.getCharset();
    }

    @Override
    public boolean isContentCached() {
        return resource.isContentCached();
    }

    @Override
    public boolean getExists() throws ResourceException {
        return resource.getExists();
    }

    @Override
    public boolean getHasEmptyContent() throws ResourceException {
        return resource.getHasEmptyContent();
    }

    @Override
    public Reader getAsReader() throws ResourceException {
        return resource.getAsReader();
    }

    @Override
    public String getText() throws ResourceException {
        return resource.getText();
    }

    @Override
    public String getDisplayName() {
        return resource.getDisplayName();
    }

    /**
     * Needed so that we can hide where a cached impl comes from. This will always respond with what
     * the user expects, not what Gradle is actually doing.
     */
    private class ProxyUriResourceLocation implements ResourceLocation {
        @Override
        public String getDisplayName() {
            return ProxyUriTextResource.this.getDisplayName();
        }

        @Nullable
        @Override
        public File getFile() {
            return null;
        }

        @Nullable
        @Override
        public URI getURI() {
            return sourceUri;
        }
    }
}
