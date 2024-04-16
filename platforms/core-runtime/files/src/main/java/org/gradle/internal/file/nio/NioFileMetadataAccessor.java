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
package org.gradle.internal.file.nio;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileMetadataAccessor;
import org.gradle.internal.file.impl.DefaultFileMetadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@SuppressWarnings("Since15")
public class NioFileMetadataAccessor implements FileMetadataAccessor {
    @Override
    public FileMetadata stat(File file) {
        Path path = file.toPath();
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return DefaultFileMetadata.missing(AccessType.DIRECT);
        }
        AccessType accessType = AccessType.viaSymlink(attributes.isSymbolicLink());
        if (accessType == AccessType.VIA_SYMLINK) {
            try {
                attributes = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (IOException e) {
                return DefaultFileMetadata.missing(AccessType.VIA_SYMLINK);
            }
        }
        if (attributes.isDirectory()) {
            return DefaultFileMetadata.directory(accessType);
        }
        if (attributes.isOther()) {
            throw new UncheckedIOException("Unsupported file type for " + file.getAbsolutePath());
        }
        return DefaultFileMetadata.file(attributes.lastModifiedTime().toMillis(), attributes.size(), accessType);
    }
}
