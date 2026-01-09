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
package org.gradle.internal.resource.local;

import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.LocalBinaryResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;

public class FileResourceConnector implements FileResourceRepository {
    private final FileSystem fileSystem;
    private final FileResourceListener listener;

    public FileResourceConnector(FileSystem fileSystem, ListenerManager listenerManager) {
        this.fileSystem = fileSystem;
        this.listener = listenerManager.getBroadcaster(FileResourceListener.class);
    }

    @Override
    public ExternalResourceRepository withProgressLogging() {
        return this;
    }

    @Override
    public ExternalResource resource(ExternalResourceName resource, boolean revalidate, @Nullable File partPosition) {
        return resource(resource);
    }

    @Override
    public LocalBinaryResource localResource(File file) {
        return new LocalFileStandInExternalResource(file, fileSystem, listener);
    }

    @Override
    public LocallyAvailableExternalResource resource(ExternalResourceName resource, boolean revalidate) {
        return resource(resource);
    }

    @Override
    public LocallyAvailableExternalResource resource(ExternalResourceName location) {
        File localFile = getFile(location);
        return new LocalFileStandInExternalResource(localFile, fileSystem, listener);
    }

    @Override
    public LocallyAvailableExternalResource resource(File file) {
        return new LocalFileStandInExternalResource(file, fileSystem, listener);
    }

    @Override
    public LocallyAvailableExternalResource resource(File file, URI originUri, ExternalResourceMetaData originMetadata) {
        return new DefaultLocallyAvailableExternalResource(originUri, file, originMetadata, fileSystem);
    }

    private static File getFile(ExternalResourceName location) {
        return new File(location.getUri());
    }
}
