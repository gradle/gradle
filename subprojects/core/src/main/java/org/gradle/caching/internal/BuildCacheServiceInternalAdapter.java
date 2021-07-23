/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.caching.internal;

import com.google.common.io.ByteStreams;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class BuildCacheServiceInternalAdapter implements BuildCacheServiceInternal {
    private final BuildCacheService delegate;

    public BuildCacheServiceInternalAdapter(BuildCacheService delegate) {
        this.delegate = delegate;
    }

    @Override
    public BuildCacheStoreOutcomeInternal store(BuildCacheKey key, BuildCacheEntryInternal entry) {
        class Writer implements BuildCacheEntryWriter {
            boolean opened;

            @Override
            public void writeTo(OutputStream output) throws IOException {
                opened = true;
                try (
                    OutputStream out = output;
                    InputStream input = Files.newInputStream(entry.getFile().toPath())
                ) {
                    ByteStreams.copy(input, out);
                }
            }

            @Override
            public long getSize() {
                return entry.getFile().length();
            }
        }

        Writer writer = new Writer();
        delegate.store(key, writer);
        return writer.opened ? BuildCacheStoreOutcomeInternal.STORED : BuildCacheStoreOutcomeInternal.NOT_STORED;
    }

    @Override
    public BuildCacheLoadOutcomeInternal load(BuildCacheKey key, BuildCacheEntryInternal entry) {
        boolean loaded = delegate.load(key, new BuildCacheEntryReader() {
            @Override
            public void readFrom(InputStream input) throws IOException {
                entry.getFile();
                try (
                    InputStream in = input;
                    OutputStream out = Files.newOutputStream(entry.getFile().toPath(), TRUNCATE_EXISTING)
                ) {
                    ByteStreams.copy(in, out);
                }
            }
        });
        return loaded ? BuildCacheLoadOutcomeInternal.LOADED : BuildCacheLoadOutcomeInternal.NOT_LOADED;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
