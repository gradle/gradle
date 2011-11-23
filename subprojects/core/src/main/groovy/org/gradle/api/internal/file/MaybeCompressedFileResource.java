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

package org.gradle.api.internal.file;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.internal.DescribedReadableResource;
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver;
import org.gradle.api.internal.file.archive.compression.GzipArchiver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceDoesNotExist;
import org.gradle.api.tasks.bundling.Compression;

import java.io.File;
import java.io.InputStream;

/**
 * by Szczepan Faber, created at: 11/23/11
 */
public class MaybeCompressedFileResource extends AbstractFileResource implements DescribedReadableResource {

    private final ReadableResource resource;

    public MaybeCompressedFileResource(File file) {
        super(file);
        String ext = FilenameUtils.getExtension(file.getName());
        FileResource fileResource = new FileResource(file);

        if (Compression.BZIP2.getExtension().equals(ext)) {
            resource = new Bzip2Archiver(fileResource);
        } else if (Compression.GZIP.getExtension().equals(ext)) {
            resource = new GzipArchiver(fileResource);
        } else {
            resource = fileResource;
        }
    }

    public InputStream read() throws ResourceDoesNotExist {
        return resource.read();
    }

    public ReadableResource getResource() {
        return resource;
    }
}
