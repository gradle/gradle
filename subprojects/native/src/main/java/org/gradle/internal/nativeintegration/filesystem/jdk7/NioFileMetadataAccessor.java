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
package org.gradle.internal.nativeintegration.filesystem.jdk7;

import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileMetadataSnapshot.AccessType;
import org.gradle.internal.file.impl.DefaultFileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@SuppressWarnings("Since15")
public class NioFileMetadataAccessor implements FileMetadataAccessor {
    @Override
    public FileMetadataSnapshot stat(File file) {
        Path path = file.toPath();
        BasicFileAttributes attributes;
        try {
            attributes = java.nio.file.Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return DefaultFileMetadataSnapshot.missing(AccessType.DIRECT);
        }
        AccessType accessType = attributes.isSymbolicLink() ? AccessType.VIA_SYMLINK : AccessType.DIRECT;
        if (accessType == AccessType.VIA_SYMLINK) {
            try {
                attributes = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (IOException e) {
                return DefaultFileMetadataSnapshot.missing(AccessType.VIA_SYMLINK);
            }
        }
        if (attributes.isDirectory()) {
            return DefaultFileMetadataSnapshot.directory(accessType);
        }
        if (attributes.isOther()) {
            return DefaultFileMetadataSnapshot.missing(accessType);
        }
        return DefaultFileMetadataSnapshot.file(attributes.lastModifiedTime().toMillis(), attributes.size(), accessType);
    }
}
