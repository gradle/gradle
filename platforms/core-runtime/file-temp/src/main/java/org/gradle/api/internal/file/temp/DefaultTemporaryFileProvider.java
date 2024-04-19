/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file.temp;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class DefaultTemporaryFileProvider implements TemporaryFileProvider {
    private final Factory<File> baseDirFactory;

    public DefaultTemporaryFileProvider(final Factory<File> fileFactory) {
        this.baseDirFactory = fileFactory;
    }

    @SuppressWarnings("Since15")
    @Override
    public File newTemporaryFile(String... path) {
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) {
                pathBuilder.append("/");
            }
            pathBuilder.append(path[i]);
        }
        return new File(baseDirFactory.create(), pathBuilder.toString()).toPath().normalize().toFile();
    }

    @Override
    public File newTemporaryDirectory(String... path) {
        File dir = newTemporaryFile(path);
        forceMkdir(dir);
        return dir;
    }

    @Override
    public Factory<File> temporaryDirectoryFactory(final String... path) {
        return new Factory<File>() {
            @Nullable
            @Override
            public File create() {
                return newTemporaryDirectory(path);
            }
        };
    }

    @Override
    public File createTemporaryFile(String prefix, @Nullable String suffix, String... path) {
        File dir = newTemporaryFile(path);
        forceMkdir(dir);
        try {
            return TempFiles.createTempFile(prefix, suffix, dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public File createTemporaryDirectory(String prefix, @Nullable String suffix, String... path) {
        File dir = newTemporaryFile(path);
        forceMkdir(dir);
        try {
            // TODO: This is not a great paradigm for creating a temporary directory.
            // See http://guava-libraries.googlecode.com/svn/tags/release08/javadoc/com/google/common/io/Files.html#createTempDir%28%29 for an alternative.
            File tmpDir = TempFiles.createTempFile(prefix, suffix, dir);
            if (!tmpDir.delete()) {
                throw new IOException("Failed to delete file: " + tmpDir);
            }
            if (!tmpDir.mkdir()) {
                throw new IOException("Failed to make directory: " + tmpDir);
            }
            return tmpDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static File forceMkdir(File directory) {
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new UncheckedIOException("Cannot create directory '" + directory + "'.");
        } else {
            return directory;
        }
    }
}
