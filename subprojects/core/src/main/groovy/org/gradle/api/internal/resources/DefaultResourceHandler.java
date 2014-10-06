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

package org.gradle.api.internal.resources;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.MaybeCompressedFileResource;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver;
import org.gradle.api.internal.file.archive.compression.GzipArchiver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.resources.TextResource;

import java.io.File;
import java.nio.charset.Charset;

public class DefaultResourceHandler implements ResourceHandler {
    private final FileOperations fileOperations;
    private final TemporaryFileProvider tempFileProvider;

    public DefaultResourceHandler(FileOperations fileOperations, TemporaryFileProvider tempFileProvider) {
        this.fileOperations = fileOperations;
        this.tempFileProvider = tempFileProvider;
    }

    public ReadableResource gzip(Object path) {
        return new GzipArchiver(fileOperations.getFileResolver().resolveResource(path));
    }

    public ReadableResource bzip2(Object path) {
        return new Bzip2Archiver(fileOperations.getFileResolver().resolveResource(path));
    }

    //this method is not on the interface, at least for now
    public ReadableResource maybeCompressed(Object tarPath) {
        if (tarPath instanceof ReadableResource) {
            return (ReadableResource) tarPath;
        } else {
            return new MaybeCompressedFileResource(fileOperations.getFileResolver().resolveResource(tarPath));
        }
    }

    public TextResource text(String string) {
        return new StringBackedTextResource(tempFileProvider, string);
    }

    public TextResource text(File file) {
        return text(file, Charset.defaultCharset().name());
    }

    public TextResource text(File file, String charset) {
        return text(fileOperations.files(file), charset);
    }

    public TextResource text(FileCollection file) {
        return text(file, Charset.defaultCharset().name());
    }

    public TextResource text(FileCollection file, String charset) {
        return new FileCollectionBackedTextResource(file, Charset.forName(charset));
    }

    public TextResource archiveText(File archive, String entryPath) {
        return archiveText(archive, entryPath, Charset.defaultCharset().name());
    }

    public TextResource archiveText(File archive, String entryPath, String charset) {
        return archiveText(fileOperations.files(archive), entryPath, charset);
    }

    public TextResource archiveText(FileCollection archive, String entryPath) {
        return archiveText(archive, entryPath, Charset.defaultCharset().name());
    }

    public TextResource archiveText(FileCollection archive, String entryPath, String charset) {
        return new FileCollectionBackedArchiveTextResource(fileOperations, archive, entryPath, Charset.forName(charset));
    }
}