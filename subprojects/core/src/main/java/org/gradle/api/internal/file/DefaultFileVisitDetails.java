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

public class DefaultFileVisitDetails extends DefaultFileTreeElement implements FileVisitDetails {
    private final AtomicBoolean stop;
    private final boolean isDirectory;
    private final long size;
    private final long lastModified;

    public DefaultFileVisitDetails(File file, RelativePath relativePath, AtomicBoolean stop, Chmod chmod, Stat stat) {
        this(file, relativePath, stop, chmod, stat, !relativePath.isFile());
    }

    public DefaultFileVisitDetails(File file, RelativePath relativePath, AtomicBoolean stop, Chmod chmod, Stat stat, boolean isDirectory) {
        this(file, relativePath, stop, chmod, stat, isDirectory, file.lastModified(), file.length());
    }

    public DefaultFileVisitDetails(File file, RelativePath relativePath, AtomicBoolean stop, Chmod chmod, Stat stat, boolean isDirectory, long lastModified, long size) {
        super(file, relativePath, chmod, stat);
        this.stop = stop;
        this.isDirectory = isDirectory;
        this.lastModified = lastModified;
        this.size = size;
    }

    public DefaultFileVisitDetails(File file, Chmod chmod, Stat stat) {
        this(file, new RelativePath(!file.isDirectory(), file.getName()), new AtomicBoolean(), chmod, stat);
    }

    @Override
    public boolean isDirectory() {
        return isDirectory;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public void stopVisiting() {
        stop.set(true);
    }
}
