/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testkit.runner.internal.file;

import org.gradle.api.UncheckedIOException;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;

public class TestNGTemporaryDirectoryProvider implements TemporaryDirectoryProvider {

    private File temporaryDirectory;

    @Override
    public void create() {
        try {
            temporaryDirectory = createTemporaryDirectory();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create temporary directory", e);
        }
    }

    private File createTemporaryDirectory() throws IOException {
        File createdTemporaryDirectory = File.createTempFile("testng", "");
        createdTemporaryDirectory.delete();
        createdTemporaryDirectory.mkdir();
        return createdTemporaryDirectory;
    }

    @Override
    public void destroy() {
        GFileUtils.deleteDirectory(temporaryDirectory);
    }

    @Override
    public File getDirectory() {
        if (temporaryDirectory == null) {
            throw new IllegalStateException("The temporary directory has not yet been created");
        }

        return temporaryDirectory;
    }
}
