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

package org.gradle.api.internal.tasks.cache;

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LocalDirectoryTaskOutputCache implements TaskOutputCache {
    private final File directory;

    public LocalDirectoryTaskOutputCache(File directory) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be a directory", directory));
            }
            if (!directory.canRead()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be readable", directory));
            }
            if (!directory.canWrite()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be writable", directory));
            }
        } else {
            if (!directory.mkdirs()) {
                throw new UncheckedIOException(String.format("Could not create cache directory: %s", directory));
            }
        }
        this.directory = directory;
    }

    @Override
    public boolean load(TaskCacheKey key, TaskOutputReader reader) throws IOException {
        final File file = getFile(key.getHashCode());
        if (file.isFile()) {
            FileInputStream stream = new FileInputStream(file);
            try {
                reader.readFrom(stream);
                return true;
            } finally {
                stream.close();
            }
        }
        return false;
    }

    @Override
    public void store(TaskCacheKey key, TaskOutputWriter result) throws IOException {
        File file = getFile(key.getHashCode());
        OutputStream output = new FileOutputStream(file);
        try {
            result.writeTo(output);
        } finally {
            output.close();
        }
    }

    private File getFile(String key) {
        return new File(directory, key);
    }

    @Override
    public String getDescription() {
        return "local directory cache in " + directory;
    }
}
