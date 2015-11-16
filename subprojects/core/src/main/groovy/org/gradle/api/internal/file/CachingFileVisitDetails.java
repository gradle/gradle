/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Stat;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class CachingFileVisitDetails extends DefaultFileVisitDetails {
    private final static FileSystem DEFAULT_FILESYSTEM = FileSystems.getDefault();
    private final boolean isDirectory;
    private volatile long size = -1;
    private volatile long lastModified = -1;

    public CachingFileVisitDetails(File file, RelativePath relativePath, AtomicBoolean stop, Chmod chmod, Stat stat, boolean isDirectory) {
        super(file, relativePath, stop, chmod, stat);
        this.isDirectory = isDirectory;
    }

    public CachingFileVisitDetails(File file) {
        this(file, new RelativePath(true, file.getName()), new AtomicBoolean(), DEFAULT_FILESYSTEM, DEFAULT_FILESYSTEM, false);
    }

    @Override
    public boolean isDirectory() {
        return isDirectory;
    }

    @Override
    public long getSize() {
        if (size == -1) {
            size = super.getSize();
        }
        return size;
    }

    @Override
    public long getLastModified() {
        if (lastModified == -1) {
            lastModified = super.getLastModified();
        }
        return lastModified;
    }
}
