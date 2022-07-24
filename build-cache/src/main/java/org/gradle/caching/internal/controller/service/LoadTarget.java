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

package org.gradle.caching.internal.controller.service;

import com.google.common.io.Closer;
import com.google.common.io.Files;
import org.gradle.caching.BuildCacheEntryReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class LoadTarget implements BuildCacheEntryReader {

    private final File file;
    private boolean loaded;

    public LoadTarget(File file) {
        this.file = file;
    }

    @Override
    public void readFrom(InputStream input) throws IOException {
        Closer closer = Closer.create();
        closer.register(input);
        try {
            if (loaded) {
                throw new IllegalStateException("Build cache entry has already been read");
            }
            Files.asByteSink(file).writeFrom(input);
            loaded = true;
        } catch (Exception e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public File getFile() {
        return file;
    }

    public long getLoadedSize() {
        if (loaded) {
            return file.length();
        } else {
            return -1;
        }
    }

}
