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

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.MaybeCompressedFileResource;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver;
import org.gradle.api.internal.file.archive.compression.GzipArchiver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.resources.TextResource;

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

    public TextResource fileText(Object file) {
        return fileText(file, Charset.defaultCharset().name());
    }

    public TextResource fileText(Object file, String charset) {
        return new FileCollectionBackedTextResource(fileOperations.files(file), Charset.forName(charset));
    }

    public TextResource archiveEntryText(Object archive, String entryPath) {
        return archiveEntryText(archive, entryPath, Charset.defaultCharset().name());
    }

    public TextResource archiveEntryText(Object archive, String entryPath, String charset) {
        return new FileCollectionBackedArchiveTextResource(fileOperations, fileOperations.files(archive), entryPath, Charset.forName(charset));
    }
}