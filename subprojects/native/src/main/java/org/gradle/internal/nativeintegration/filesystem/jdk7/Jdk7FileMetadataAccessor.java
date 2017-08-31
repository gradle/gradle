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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@SuppressWarnings("Since15")
public class Jdk7FileMetadataAccessor implements FileMetadataAccessor {
    @Override
    public FileMetadataSnapshot stat(File f) {
        if (!f.exists()) {
            // This is really not cool, but we cannot rely on `readAttributes` because it will
            // THROW AN EXCEPTION if the file is missing, which is really incredibly slow just
            // to determine if a file exists or not.
            return DefaultFileMetadata.missing();
        }
        try {
            BasicFileAttributes bfa = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            if (bfa.isDirectory()) {
                return DefaultFileMetadata.directory();
            }
            return new DefaultFileMetadata(FileType.RegularFile, bfa.lastModifiedTime().toMillis(), bfa.size());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public FileMetadataSnapshot stat(Path path) throws IOException {
        if (!Files.exists(path)) {
            return DefaultFileMetadata.missing();
        }
        BasicFileAttributes bfa = Files.readAttributes(path, BasicFileAttributes.class);
        if (bfa.isDirectory()) {
            return DefaultFileMetadata.directory();
        }
        return new DefaultFileMetadata(FileType.RegularFile, bfa.lastModifiedTime().toMillis(), bfa.size());
    }
}
