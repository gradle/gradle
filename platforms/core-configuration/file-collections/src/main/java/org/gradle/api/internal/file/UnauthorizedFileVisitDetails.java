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
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.RelativePath;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class UnauthorizedFileVisitDetails implements FileVisitDetails {
    private File file;
    private RelativePath relativePath;

    public UnauthorizedFileVisitDetails(File file, RelativePath relativePath) {
        this.file = file;
        this.relativePath = relativePath;
    }

    @Override
    public void stopVisiting() {
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public boolean isDirectory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream open() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyTo(OutputStream output) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean copyTo(File target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public String getPath() {
        return getRelativePath().getPathString();
    }

    @Override
    public RelativePath getRelativePath() {
        return relativePath;
    }

    @Override
    public int getMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilePermissions getPermissions() {
        throw new UnsupportedOperationException();
    }
}
