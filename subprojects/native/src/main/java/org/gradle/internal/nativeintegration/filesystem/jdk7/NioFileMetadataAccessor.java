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
    public FileMetadataSnapshot stat(File f) {
        try {
            Path path = f.toPath();
            BasicFileAttributes bfa = java.nio.file.Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            AccessType accessType = bfa.isSymbolicLink() ? AccessType.VIA_SYMLINK : AccessType.DIRECT;
            if (accessType == AccessType.VIA_SYMLINK) {
                try {
                    bfa = Files.readAttributes(path, BasicFileAttributes.class);
                } catch (IOException e) {
                    return DefaultFileMetadataSnapshot.missing(AccessType.VIA_SYMLINK);
                }
            }
            if (bfa.isDirectory()) {
                return DefaultFileMetadataSnapshot.directory(accessType);
            }
            if (bfa.isOther()) {
                return DefaultFileMetadataSnapshot.missing(accessType);
            }
            return DefaultFileMetadataSnapshot.file(bfa.lastModifiedTime().toMillis(), bfa.size(), accessType);
        } catch (IOException e) {
            return DefaultFileMetadataSnapshot.missing(AccessType.DIRECT);
        }
    }
}
