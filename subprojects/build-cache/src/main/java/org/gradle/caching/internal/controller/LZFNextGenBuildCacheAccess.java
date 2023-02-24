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

import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.file.BufferProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;

public class LZFNextGenBuildCacheAccess implements NextGenBuildCacheAccess {
    private final NextGenBuildCacheAccess delegate;
    private final BufferProvider bufferProvider;

    public LZFNextGenBuildCacheAccess(NextGenBuildCacheAccess delegate, BufferProvider bufferProvider) {
        this.delegate = delegate;
        this.bufferProvider = bufferProvider;
    }

    @Override
    public <T> void load(Map<BuildCacheKey, T> entries, LoadHandler<T> handler) {
        delegate.load(entries, (inputStream, payload) -> {
            try (LZFInputStream lzfInput = new LZFInputStream(inputStream)) {
                handler.handle(lzfInput, payload);
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
                public InputStream openStream() {
                    return null;
                }

                @Override
                public void writeTo(OutputStream output) throws IOException {
                    try (LZFOutputStream compressed = new LZFOutputStream(output)) {
                        delegateWriter.writeTo(compressed);
                    }
                }

                @Override
                public long getSize() {
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
