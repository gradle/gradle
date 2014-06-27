/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.cached;

import org.gradle.internal.resource.LocalFileStandInExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Creates an ExternalResource from something that has been cached locally.
 */
public class CachedExternalResourceAdapter extends LocalFileStandInExternalResource {
    private final LocallyAvailableResource local;
    private final ExternalResourceAccessor accessor;
    private final HashValue expectedChecksum;

    public CachedExternalResourceAdapter(URI source, LocallyAvailableResource local, ExternalResourceAccessor accessor, ExternalResourceMetaData remoteMetaData, HashValue expectedChecksum) {
        super(source, local.getFile(), remoteMetaData);
        this.local = local;
        this.accessor = accessor;
        this.expectedChecksum = expectedChecksum;
    }

    @Override
    public InputStream openStream() throws IOException {
        return new FileInputStream(local.getFile());
    }

    public void writeTo(File destination) throws IOException {
        try {
            super.writeTo(destination);
        } catch (IOException e) {
            downloadResourceDirect(destination);
            return;
        }

        // If the checksum of the downloaded file does not match the cached artifact, download it directly.
        // This may be the case if the cached artifact was changed before copying
        if (!getSha1(destination).equals(expectedChecksum)) {
            downloadResourceDirect(destination);
        }
    }

    private void downloadResourceDirect(File destination) throws IOException {
        // Perform a regular download, without considering external caches
        accessor.getResource(getURI()).writeTo(destination);
    }

}
