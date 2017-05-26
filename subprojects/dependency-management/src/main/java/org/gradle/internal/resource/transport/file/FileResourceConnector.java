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
package org.gradle.internal.resource.transport.file;

import org.gradle.api.Nullable;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.DefaultLocallyAvailableExternalResource;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.ExternalResourceRepository;

import java.io.File;

public class FileResourceConnector implements ExternalResourceRepository {
    private final FileSystem fileSystem;

    public FileResourceConnector(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public ExternalResourceRepository withProgressLogging() {
        return this;
    }

    @Override
    public LocallyAvailableExternalResource resource(ExternalResourceName resource, boolean revalidate) {
        return resource(resource);
    }

    @Override
    public LocallyAvailableExternalResource resource(ExternalResourceName location) {
        File localFile = getFile(location);
        return new DefaultLocallyAvailableExternalResource(location.getUri(), new DefaultLocallyAvailableResource(localFile), fileSystem);
    }

    @Nullable
    public LocallyAvailableExternalResource getResourceIfPresent(ExternalResourceName location) {
        File localFile = getFile(location);
        if (!localFile.exists()) {
            return null;
        }
        return new DefaultLocallyAvailableExternalResource(location.getUri(), new DefaultLocallyAvailableResource(localFile), fileSystem);
    }

    private static File getFile(ExternalResourceName location) {
        return new File(location.getUri());
    }
}
