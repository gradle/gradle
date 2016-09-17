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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

class MissingFileVisitDetails implements FileVisitDetails {
    private final File file;
    private final RelativePath relativePath;

    public MissingFileVisitDetails(File file) {
        this.file = file;
        this.relativePath = new RelativePath(true, file.getName());
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public String getPath() {
        return file.getName();
    }

    @Override
    public RelativePath getRelativePath() {
        return relativePath;
    }

    @Override
    public int getMode() {
        return 0;
    }

    @Override
    public long getLastModified() {
        return 0L;
    }

    @Override
    public long getSize() {
        return 0L;
    }

    @Override
    public InputStream open() {
        throw new UncheckedIOException(new FileNotFoundException(file.getAbsolutePath()));
    }

    @Override
    public void copyTo(OutputStream output) {
        throw new UncheckedIOException(new FileNotFoundException(file.getAbsolutePath()));
    }

    @Override
    public boolean copyTo(File target) {
        throw new UncheckedIOException(new FileNotFoundException(file.getAbsolutePath()));
    }

    @Override
    public void stopVisiting() {
        // Ignore
    }
}
