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

import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Nullable;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Used when we find a file locally that matches the checksum of some external resource.
 *
 * It saves us downloading the file, but we don't get any metadata for it.
 */
public class LocalFileStandInExternalResource extends AbstractExternalResource {
    private final File localFile;
    private final URI source;
    private final FileSystem fileSystem;
    private ExternalResourceMetaData metaData;

    public LocalFileStandInExternalResource(URI source, File localFile, @Nullable ExternalResourceMetaData metaData, FileSystem fileSystem) {
        this.source = source;
        this.localFile = localFile;
        this.metaData = metaData;
        this.fileSystem = fileSystem;
    }

    public URI getURI() {
        return source;
    }

    public long getContentLength() {
        return localFile.length();
    }

    @Override
    public String getDisplayName() {
        if (source.equals(localFile.toURI())) {
            return localFile.getPath();
        }
        return source.toString();
    }

    public InputStream openStream() throws IOException {
        if (!localFile.exists()) {
            return null;
        }
        return new FileInputStream(localFile);
    }

    @Nullable
    public ExternalResourceMetaData getMetaData() {
        if (metaData == null) {
            FileMetadataSnapshot stat = fileSystem.stat(localFile);
            if (stat.getType() == FileType.Missing) {
                return null;
            }
            return new DefaultExternalResourceMetaData(source, stat.getLastModified(), stat.getLength());
        }
        return metaData;
    }

    @Override
    public void put(LocalResource location) {
        try {
            if (!localFile.canWrite()) {
                localFile.delete();
            }
            Files.createParentDirs(localFile);

            InputStream input = location.open();
            try {
                FileOutputStream output = new FileOutputStream(localFile);
                try {
                    IOUtils.copyLarge(input, output);
                } finally {
                    output.close();
                }
            } finally {
                input.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.putFailed(getURI(), e);
        }
    }

    @Nullable
    @Override
    public List<String> list() throws ResourceException {
        if (localFile.isDirectory()) {
            String[] names = localFile.list();
            return Arrays.asList(names);
        }
        return null;
    }
}
