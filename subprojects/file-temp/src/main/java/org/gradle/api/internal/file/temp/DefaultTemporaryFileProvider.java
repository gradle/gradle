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
import org.gradle.internal.FileUtils;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

public class DefaultTemporaryFileProvider implements TemporaryFileProvider, Serializable {
    private final Factory<File> baseDirFactory;

    public DefaultTemporaryFileProvider(final Factory<File> fileFactory) {
        this.baseDirFactory = fileFactory;
    }

    @Override
    public File newTemporaryFile(String... path) {
        return FileUtils.canonicalize(new File(baseDirFactory.create(), CollectionUtils.join("/", path)));
    }

    @Override
    public File createTemporaryFile(String prefix, @Nullable String suffix, String... path) {
        File dir = newTemporaryFile(path);
        GFileUtils.mkdirs(dir);
        try {
            return TempFiles.createTempFile(prefix, suffix, dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public File createTemporaryDirectory(String prefix, @Nullable String suffix, String... path) {
        File dir = newTemporaryFile(path);
        GFileUtils.mkdirs(dir);
        try {
            // TODO: This is not a great paradigm for creating a temporary directory.
            // See http://guava-libraries.googlecode.com/svn/tags/release08/javadoc/com/google/common/io/Files.html#createTempDir%28%29 for an alternative.
            File tmpDir = TempFiles.createTempFile(prefix, suffix, dir);
            if (!tmpDir.delete()) {
                throw new UncheckedIOException("Failed to delete file: " + tmpDir);
            }
            if (!tmpDir.mkdir()) {
                throw new UncheckedIOException("Failed to make directory: " + tmpDir);
            }
            return tmpDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
