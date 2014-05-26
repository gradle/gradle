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

package org.gradle.internal.resource;

import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Used when we find a file locally that matches the checksum of some external resource.
 *
 * It saves us downloading the file, but we don't get any metadata for it.
 */
public class LocalFileStandInExternalResource extends AbstractExternalResource {
    private final File localFile;
    private final URI source;
    private ExternalResourceMetaData metaData;

    public LocalFileStandInExternalResource(URI source, File localFile, ExternalResourceMetaData metaData) {
        this.source = source;
        this.localFile = localFile;
        this.metaData = metaData;
    }

    public URI getURI() {
        return source;
    }

    public long getLastModifiedTime() {
        return localFile.lastModified();
    }

    public long getContentLength() {
        return localFile.length();
    }

    public boolean isLocal() {
        return true;
    }

    public InputStream openStream() throws IOException {
        return new FileInputStream(localFile);
    }

    public ExternalResourceMetaData getMetaData() {
        if (metaData == null) {
            metaData = new DefaultExternalResourceMetaData(source, getLastModifiedTime(), getContentLength(), null, null);
        }
        return metaData;
    }

    protected HashValue getSha1(File contentFile) {
        return HashUtil.createHash(contentFile, "SHA1");
    }
}
