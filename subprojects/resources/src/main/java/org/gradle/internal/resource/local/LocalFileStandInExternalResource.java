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

import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.resource.AbstractExternalResource;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceReadResult;
import org.gradle.internal.resource.ExternalResourceWriteResult;
import org.gradle.internal.resource.LocalBinaryResource;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * A file backed {@link ExternalResource} implementation.
 */
public class LocalFileStandInExternalResource extends AbstractExternalResource implements LocallyAvailableExternalResource, LocalBinaryResource {
    private final File localFile;
    private final FileSystem fileSystem;

    public LocalFileStandInExternalResource(File localFile, FileSystem fileSystem) {
        this.localFile = localFile;
        this.fileSystem = fileSystem;
    }

    @Override
    public URI getURI() {
        return localFile.toURI();
    }

    @Override
    public File getFile() {
        return localFile;
    }

    @Override
    public String getBaseName() {
        return localFile.getName();
    }

    @Override
    public File getContainingFile() {
        return localFile;
    }

    @Override
    public long getContentLength() {
        return localFile.length();
    }

    @Override
    public String getDisplayName() {
        return localFile.getPath();
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
    public ExternalResourceReadResult<Void> writeTo(OutputStream output) {
        if (!localFile.exists()) {
            throw ResourceExceptions.getMissing(getURI());
        }
        try {
            CountingInputStream input = new CountingInputStream(new FileInputStream(localFile));
            try {
                IOUtils.copyLarge(input, output);
            } finally {
                input.close();
            }
            return ExternalResourceReadResult.of(input.getCount());
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    @Override
    @Nullable
    public ExternalResourceReadResult<Void> writeToIfPresent(File destination) {
        if (!localFile.exists()) {
            return null;
        }
        try {
            CountingInputStream input = new CountingInputStream(new FileInputStream(localFile));
            try {
                FileOutputStream output = new FileOutputStream(destination);
                try {
                    IOUtils.copyLarge(input, output);
                } finally {
                    output.close();
                }
            } finally {
                input.close();
            }
            return ExternalResourceReadResult.of(input.getCount());
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    public ExternalResourceReadResult<Void> withContent(Action<? super InputStream> readAction) {
        if (!localFile.exists()) {
            throw ResourceExceptions.getMissing(getURI());
        }
        try {
            CountingInputStream input = new CountingInputStream(new BufferedInputStream(new FileInputStream(localFile)));
            try {
                readAction.execute(input);
            } finally {
                input.close();
            }
            return ExternalResourceReadResult.of(input.getCount());
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(ContentAction<? extends T> readAction) throws ResourceException {
        if (!localFile.exists()) {
            return null;
        }
        try {
            CountingInputStream input = new CountingInputStream(new BufferedInputStream(new FileInputStream(localFile)));
            try {
                T resourceReadResult = readAction.execute(input, getMetaData());
                return ExternalResourceReadResult.of(input.getCount(), resourceReadResult);
            } finally {
                input.close();
            }
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException {
        if (!localFile.exists()) {
            return null;
        }
        try {
            CountingInputStream input = new CountingInputStream(new BufferedInputStream(new FileInputStream(localFile)));
            try {
                T resourceReadResult = readAction.transform(input);
                return ExternalResourceReadResult.of(input.getCount(), resourceReadResult);
            } finally {
                input.close();
            }
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    @Override
    public ExternalResourceWriteResult put(ReadableContent location) {
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
        } catch (IOException e) {
            throw ResourceExceptions.putFailed(getURI(), e);
        }
    }

    @Override
    public InputStream open() throws ResourceException {
        if (localFile.isDirectory()) {
            throw ResourceExceptions.readFolder(localFile);
        }
        try {
            return new FileInputStream(localFile);
        } catch (FileNotFoundException e) {
            throw ResourceExceptions.readMissing(localFile, e);
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
