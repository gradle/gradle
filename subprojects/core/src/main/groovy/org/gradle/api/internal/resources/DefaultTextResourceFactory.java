/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.resources.TextResource;
import org.gradle.api.resources.TextResourceFactory;

import java.nio.charset.Charset;

public class DefaultTextResourceFactory implements TextResourceFactory {
    private final FileOperations fileOperations;
    private final TemporaryFileProvider tempFileProvider;

    public DefaultTextResourceFactory(FileOperations fileOperations, TemporaryFileProvider tempFileProvider) {
        this.fileOperations = fileOperations;
        this.tempFileProvider = tempFileProvider;
    }

    public TextResource fromString(String string) {
        return new StringBackedTextResource(tempFileProvider, string);
    }

    public TextResource fromFile(Object file, String charset) {
        return new FileCollectionBackedTextResource(tempFileProvider, fileOperations.files(file), Charset.forName(charset));
    }

    public TextResource fromFile(Object file) {
        return fromFile(file, Charset.defaultCharset().name());

    }

    public TextResource fromArchiveEntry(Object archive, String entryPath, String charset) {
        return new FileCollectionBackedArchiveTextResource(fileOperations, tempFileProvider, fileOperations.files(archive), entryPath, Charset.forName(charset));
    }

    public TextResource fromArchiveEntry(Object archive, String entryPath) {
        return fromArchiveEntry(archive, entryPath, Charset.defaultCharset().name());
    }
}
