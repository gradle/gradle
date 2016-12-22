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

import net.rubygrapefruit.platform.FileInfo;
import net.rubygrapefruit.platform.Files;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.File;

public class NativePlatformBackedFileMetadataAccessor implements FileMetadataAccessor {
    private final Files files;

    public NativePlatformBackedFileMetadataAccessor(Files files) {
        this.files = files;
    }

    @Override
    public FileMetadataSnapshot stat(File f) {
        FileInfo stat = files.stat(f, true);
        switch (stat.getType()) {
            case File:
                if (!f.isFile()) {
                    throw new IllegalStateException("File " + f + " is not a file.");
                }
                return new FileMetadataSnapshot(FileType.RegularFile, stat.getLastModifiedTime(), stat.getSize());
            case Directory:
                if (!f.isDirectory()) {
                    throw new IllegalStateException("File " + f + " is not a directory.");
                }
                return FileMetadataSnapshot.directory();
            case Missing:
                if (f.exists()) {
                    throw new IllegalStateException("File " + f + " should not exist.");
                }
                return FileMetadataSnapshot.missing();
            default:
                throw new IllegalArgumentException("Unrecognised file type: " + stat.getType());
        }
    }
}
