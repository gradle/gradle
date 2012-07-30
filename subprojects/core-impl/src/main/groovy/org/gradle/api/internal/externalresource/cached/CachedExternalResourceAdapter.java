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
package org.gradle.api.internal.externalresource.cached;

import org.gradle.api.internal.externalresource.LocalFileStandInExternalResource;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor;

import java.io.File;
import java.io.IOException;

/**
 * Creates an ExternalResource from something that has been cached locally.
 */
public class CachedExternalResourceAdapter extends LocalFileStandInExternalResource {
    private final CachedExternalResource cached;
    private final ExternalResourceAccessor accessor;

    public CachedExternalResourceAdapter(String source, CachedExternalResource cached, ExternalResourceAccessor accessor) {
        this(source, cached, accessor, null);
    }

    public CachedExternalResourceAdapter(String source, CachedExternalResource cached, ExternalResourceAccessor accessor, ExternalResourceMetaData metaData) {
        super(source, cached.getCachedFile(), metaData);
        this.cached = cached;
        this.accessor = accessor;
    }

    @Override
    public String toString() {
        return "CachedResource: " + cached.getCachedFile() + " for " + getName();
    }

    public long getLastModified() {
        return cached.getExternalLastModifiedAsTimestamp();
    }

    public long getContentLength() {
        return cached.getContentLength();
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
        if (!getSha1(destination).equals(getLocalFileSha1())) {
            downloadResourceDirect(destination);
        }
    }

    private void downloadResourceDirect(File destination) throws IOException {
        // Perform a regular download, without considering external caches
        accessor.getResource(getName()).writeTo(destination);
    }

}
