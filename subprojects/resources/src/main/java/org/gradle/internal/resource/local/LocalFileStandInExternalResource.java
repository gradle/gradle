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

import com.google.common.io.CountingOutputStream;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Nullable;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.resource.AbstractExternalResource;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceWriteResult;
import org.gradle.internal.resource.LocalResource;
import org.gradle.internal.resource.ResourceExceptions;
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
 * A file backed {@link ExternalResource} implementation.
 */
public class LocalFileStandInExternalResource extends AbstractExternalResource implements LocallyAvailableExternalResource, LocallyAvailableResource {
    private final File localFile;
    private final FileSystem fileSystem;

    public LocalFileStandInExternalResource(File localFile, FileSystem fileSystem) {
        this.localFile = localFile;
        this.fileSystem = fileSystem;
    }

    public URI getURI() {
        return localFile.toURI();
    }

    @Override
    public File getFile() {
        return localFile;
    }

    @Override
    public long getLastModified() {
        return localFile.lastModified();
    }

    @Override
    public HashValue getSha1() {
        throw new UnsupportedOperationException();
    }

    @Override
    public LocallyAvailableResource getLocalResource() {
        return this;
    }

    public long getContentLength() {
        return localFile.length();
    }

    @Override
    public String getDisplayName() {
        return localFile.getPath();
    }

    public InputStream openStreamIfPresent() throws IOException {
        if (!localFile.exists()) {
            return null;
        }
        return new FileInputStream(localFile);
    }

    @Nullable
    public ExternalResourceMetaData getMetaData() {
        FileMetadataSnapshot stat = fileSystem.stat(localFile);
        if (stat.getType() == FileType.Missing) {
            return null;
        }
        return new DefaultExternalResourceMetaData(localFile.toURI(), stat.getLastModified(), stat.getLength());
    }

    @Override
    public ExternalResourceWriteResult put(LocalResource location) {
        try {
            if (!localFile.canWrite()) {
                localFile.delete();
            }
            Files.createParentDirs(localFile);

            InputStream input = location.open();
            try {
                CountingOutputStream output = new CountingOutputStream(new FileOutputStream(localFile));
                try {
                    IOUtils.copyLarge(input, output);
                } finally {
                    output.close();
                }
                return new ExternalResourceWriteResult(output.getCount());
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
