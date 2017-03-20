/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Nullable;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

public class DefaultTemporaryFileProvider implements TemporaryFileProvider, Serializable {
    private final Factory<File> baseDirFactory;
    private final Path tempDirectory;

    public DefaultTemporaryFileProvider(final Factory<File> fileFactory) {
        this.baseDirFactory = fileFactory;
        try {
            this.tempDirectory = Files.createTempDirectory("GradleTemp");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Runtime.getRuntime().addShutdownHook(new DefaultTemporaryFileCleanup(this.tempDirectory.toFile()));
    }

    public File newTemporaryFile(String... path) {
        return FileUtils.canonicalize(new File(baseDirFactory.create(), CollectionUtils.join("/", path)));
    }

    public File createTemporaryFile(String prefix, @Nullable String suffix, String... path) {
        File dir = new File(baseDirFactory.create(), CollectionUtils.join("/", path));
        GFileUtils.mkdirs(dir);
        try {
            return File.createTempFile(prefix, suffix, dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public File createTemporaryDirectory(@Nullable String prefix, @Nullable String suffix, @Nullable String... path) {
        File dir = new File(baseDirFactory.create(), CollectionUtils.join("/", path));
        GFileUtils.mkdirs(dir);
        try {
            return Files.createTempDirectory(tempDirectory, prefix + "_" + suffix).toFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
