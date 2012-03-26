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

package org.gradle.api.internal.externalresource;

import org.apache.ivy.util.CopyProgressListener;
import org.gradle.api.internal.artifacts.repositories.transport.http.HttpResourceCollection;
import org.gradle.util.hash.HashUtil;
import org.gradle.util.hash.HashValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LocalFileStandInExternalResource extends AbstractExternalResource {

    private final File localFile;
    private final String source;
    private final HttpResourceCollection resourceCollection;

    public LocalFileStandInExternalResource(String source, File localFile, HttpResourceCollection resourceCollection) {
        this.source = source;
        this.localFile = localFile;
        this.resourceCollection = resourceCollection;
    }

    public String getName() {
        return source;
    }

    public long getLastModified() {
        return -1;
    }

    public long getContentLength() {
        return localFile.length();
    }

    public boolean exists() {
        return true;
    }

    public boolean isLocal() {
        return true;
    }

    public InputStream openStream() throws IOException {
        return new FileInputStream(localFile);
    }

    public void writeTo(File destination, CopyProgressListener progress) throws IOException {
        try {
            super.writeTo(destination, progress);
        } catch (IOException e) {
            downloadResourceDirect(destination, progress);
            return;
        }

        // If the checksum of the downloaded file does not match the cached artifact, download it directly.
        // This may be the case if the cached artifact was changed before copying
        if (!getSha1(destination).equals(getLocalFileSha1())) {
            downloadResourceDirect(destination, progress);
        }
    }

    private void downloadResourceDirect(File destination, CopyProgressListener progress) throws IOException {
        // Perform a regular download, without considering external caches
        resourceCollection.getResource(source).writeTo(destination, progress);
    }

    private HashValue getSha1(File contentFile) {
        return HashUtil.createHash(contentFile, "SHA1");
    }

    protected HashValue getLocalFileSha1() {
        return getSha1(localFile);
    }
}
