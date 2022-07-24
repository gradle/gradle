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

package org.gradle.api.resources.internal;

import org.gradle.internal.resource.LocalBinaryResource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;

/**
 * Adapts a {@link LocalBinaryResource} to a {@link ReadableResourceInternal}.
 */
public class LocalResourceAdapter implements ReadableResourceInternal {
    private final LocalBinaryResource resource;

    public LocalResourceAdapter(LocalBinaryResource resource) {
        this.resource = resource;
    }

    @Override
    public File getBackingFile() {
        return resource.getContainingFile();
    }

    @Override
    public String toString() {
        return resource.getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return resource.getDisplayName();
    }

    @Override
    public InputStream read() {
        return new BufferedInputStream(resource.open());
    }

    @Override
    public URI getURI() {
        return resource.getURI();
    }

    @Override
    public String getBaseName() {
        return resource.getBaseName();
    }
}
