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
package org.gradle.api.internal.artifacts.repositories.transport.http;

import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.util.ChecksumHelper;
import org.apache.ivy.util.CopyProgressListener;
import org.gradle.api.internal.artifacts.ivyservice.filestore.CachedArtifact;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

class CachedHttpResource extends AbstractHttpResource {
    private final String source;
    private final CachedArtifact cachedArtifact;

    private final HttpResourceCollection resourceCollection;

    public CachedHttpResource(String source, CachedArtifact cachedArtifact, HttpResourceCollection resourceCollection) {
        this.source = source;
        this.cachedArtifact = cachedArtifact;
        this.resourceCollection = resourceCollection;
    }

    @Override
    public String toString() {
        return "CachedResource: " + cachedArtifact.getOrigin() + " for " + source;
    }

    public String getName() {
        return source;
    }

    public long getLastModified() {
        return cachedArtifact.getLastModified();
    }

    public long getContentLength() {
        return cachedArtifact.getContentLength();
    }

    public boolean exists() {
        return true;
    }

    public boolean isLocal() {
        return true;
    }

    public Resource clone(String cloneName) {
        throw new UnsupportedOperationException();
    }

    public InputStream openStream() throws IOException {
        return new FileInputStream(cachedArtifact.getOrigin());
    }

    public void writeTo(File destination, CopyProgressListener progress) throws IOException {
        try {
            super.writeTo(destination, progress);
        } catch (IOException e) {
            downloadResourceDirect(destination, progress);
            return;
        }

        // Check that sha1 matches after copy
        String destinationSha1 = getChecksum(destination);
        if (destinationSha1.equals(cachedArtifact.getSha1())) {
            return;
        }

        downloadResourceDirect(destination, progress);
    }

    private void downloadResourceDirect(File destination, CopyProgressListener progress) throws IOException {
        // Perform a regular download, without considering external caches
        resourceCollection.getResource(source).writeTo(destination, progress);
    }

    private String getChecksum(File contentFile) {
        // TODO:DAZ Use our own implementation, shared with DefaultCachedResource
        try {
            return ChecksumHelper.computeAsString(contentFile, "sha1");
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }
    
}
