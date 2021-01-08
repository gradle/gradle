/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.resources.internal.ReadableResourceInternal;

import java.io.File;
import java.io.InputStream;
import java.net.URI;

public class UnknownBackingFileReadableResource implements ReadableResourceInternal {
    private final ReadableResource resource;

    public UnknownBackingFileReadableResource(ReadableResource resource) {
        this.resource = resource;
    }

    @Override
    public File getBackingFile() {
        return null;
    }

    @Override
    public InputStream read() throws MissingResourceException, ResourceException {
        return resource.read();
    }

    @Override
    public String getDisplayName() {
        return resource.getDisplayName();
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
