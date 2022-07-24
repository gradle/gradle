/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem.services;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;

import java.io.File;

public class FallbackFileMetadataAccessor implements FileMetadataAccessor {
    @Override
    public FileMetadata stat(File f) {
        if (!f.exists()) {
            return DefaultFileMetadata.missing(AccessType.DIRECT);
        }
        if (f.isDirectory()) {
            return DefaultFileMetadata.directory(AccessType.DIRECT);
        }
        if (f.isFile()) {
            return DefaultFileMetadata.file(f.lastModified(), f.length(), AccessType.DIRECT);
        }
        throw new UncheckedIOException("Unsupported file type for " + f.getAbsolutePath());
    }
}
