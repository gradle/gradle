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
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver;
import org.gradle.api.internal.file.archive.compression.GzipArchiver;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.api.resources.internal.ReadableResourceInternal;

public class DefaultResourceHandler implements ResourceHandler {
    private final FileOperations fileOperations;
    private final TextResourceFactory textResourceFactory;

    public DefaultResourceHandler(FileOperations fileOperations, TemporaryFileProvider tempFileProvider) {
        this.fileOperations = fileOperations;
        textResourceFactory = new DefaultTextResourceFactory(fileOperations, tempFileProvider);
    }

    public ReadableResourceInternal gzip(Object path) {
        return new GzipArchiver(fileOperations.getFileResolver().resolveResource(path));
    }

    public ReadableResourceInternal bzip2(Object path) {
        return new Bzip2Archiver(fileOperations.getFileResolver().resolveResource(path));
    }

    public TextResourceFactory getText() {
        return textResourceFactory;
    }
}
