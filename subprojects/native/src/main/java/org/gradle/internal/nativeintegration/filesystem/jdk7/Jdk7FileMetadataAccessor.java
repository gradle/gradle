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
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class Jdk7FileMetadataAccessor implements FileMetadataAccessor {
    @Override
    public FileMetadataSnapshot stat(File f) {
        try {
            return stat(f.toPath());
        } catch (IOException e) {
            return DefaultFileMetadata.missing();
        }
    }

    @Override
    public FileMetadataSnapshot stat(Path path) throws IOException {
        try {
            BasicFileAttributes bfa = java.nio.file.Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            boolean isSymlink = bfa.isSymbolicLink();
            try {
                if (isSymlink) {
                    bfa = java.nio.file.Files.readAttributes(path, BasicFileAttributes.class);
                }
                if (bfa.isDirectory()) {
                    return isSymlink
                        ? DefaultFileMetadata.symlinkedDirectory()
                        : DefaultFileMetadata.directory();
                }
                return new DefaultFileMetadata(FileType.RegularFile, bfa.lastModifiedTime().toMillis(), bfa.size(), isSymlink);
            } catch (IOException e) {
                return DefaultFileMetadata.brokenSymlink();
            }
        } catch (IOException e) {
            return DefaultFileMetadata.missing();
        }
    }
}
