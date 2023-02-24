/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.internal.controller;

import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheKey;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZipNextGenBuildCacheAccess implements NextGenBuildCacheAccess {
    private final NextGenBuildCacheAccess delegate;

    public GZipNextGenBuildCacheAccess(NextGenBuildCacheAccess delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> void load(Map<BuildCacheKey, T> entries, LoadHandler<T> handler) {
        delegate.load(entries, (inputStream, payload) -> {
            try (GZIPInputStream zipInput = new GZIPInputStream(inputStream)) {
                handler.handle(zipInput, payload);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public <T> void store(Map<BuildCacheKey, T> entries, StoreHandler<T> handler) {
        delegate.store(entries, payload -> {
            BuildCacheEntryWriter delegateWriter = handler.handle(payload);

            return new BuildCacheEntryWriter() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    try (GZIPOutputStream zipOutput = new GZIPOutputStream(output)) {
                        delegateWriter.writeTo(zipOutput);
                    }
                }

                @Override
                public long getSize() {
                    // TODO check if we can return compressed size
                    return delegateWriter.getSize();
                }
            };
        });
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
