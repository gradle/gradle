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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZipNextGenBuildCacheAccess implements NextGenBuildCacheAccess {
    private final NextGenBuildCacheAccess delegate;

    public GZipNextGenBuildCacheAccess(NextGenBuildCacheAccess delegate) {
        this.delegate = delegate;
    }

    @Override
    public void load(Iterable<BuildCacheKey> keys, BiConsumer<BuildCacheKey, InputStream> processor) {
        delegate.load(keys, (buildCacheKey, inputStream) -> {
            try (GZIPInputStream zipInput = new GZIPInputStream(inputStream)) {
                processor.accept(buildCacheKey, zipInput);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public <T> void store(Map<BuildCacheKey, T> entries, StoreHandler<T> handler) {
        delegate.store(entries, payload -> {
            BuildCacheEntryWriter delegateWriter = handler.handle(payload);
            // TODO Make this more performant for large files
            ByteArrayOutputStream compressed = new ByteArrayOutputStream((int) (delegateWriter.getSize() * 1.2));
            try (GZIPOutputStream zipOutput = new GZIPOutputStream(compressed)) {
                delegateWriter.writeTo(zipOutput);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            byte[] compressedBytes = compressed.toByteArray();
            return new BuildCacheEntryWriter() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    output.write(compressedBytes);
                }

                @Override
                public long getSize() {
                    return compressedBytes.length;
                }
            };
        });
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
