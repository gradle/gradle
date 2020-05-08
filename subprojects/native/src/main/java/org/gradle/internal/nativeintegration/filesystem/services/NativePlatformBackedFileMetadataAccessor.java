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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.file.FileInfo;
import net.rubygrapefruit.platform.file.Files;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;

import java.io.File;

public class NativePlatformBackedFileMetadataAccessor implements FileMetadataAccessor {
    private final Files files;

    public NativePlatformBackedFileMetadataAccessor(Files files) {
        this.files = files;
    }

    @Override
    public FileMetadata stat(File f) {
        FileInfo stat;
        try {
            stat = files.stat(f, false);
        } catch (NativeException e) {
            return DefaultFileMetadata.missing(AccessType.DIRECT);
        }
        AccessType accessType = AccessType.viaSymlink(stat.getType() == FileInfo.Type.Symlink);
        if (accessType == AccessType.VIA_SYMLINK) {
            try {
                stat = files.stat(f, true);
            } catch (NativeException e) {
                return DefaultFileMetadata.missing(AccessType.VIA_SYMLINK);
            }
        }
        switch (stat.getType()) {
            case File:
                return DefaultFileMetadata.file(stat.getLastModifiedTime(), stat.getSize(), accessType);
            case Directory:
                return DefaultFileMetadata.directory(accessType);
            case Missing:
            case Other:
                return DefaultFileMetadata.missing(accessType);
            default:
                throw new IllegalArgumentException("Unrecognised file type: " + stat.getType());
        }
    }
}
