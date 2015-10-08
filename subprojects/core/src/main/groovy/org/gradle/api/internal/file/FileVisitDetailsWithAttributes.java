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

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.nativeintegration.filesystem.Stat;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileVisitDetailsWithAttributes extends DefaultFileVisitDetails implements FileVisitDetails {
    private final boolean isDirectory;
    private final long lastModified;
    private final long size;

    public FileVisitDetailsWithAttributes(File file, RelativePath relativePath, AtomicBoolean stop, Chmod chmod, Stat stat, boolean isDirectory, long lastModified, long size) {
        super(file, relativePath, stop, chmod, stat);
        this.isDirectory = isDirectory;
        this.lastModified = lastModified;
        this.size = size;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public boolean isDirectory() {
        return isDirectory;
    }
}
