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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.NextGenBuildCacheService;
import org.gradle.internal.file.BufferProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZipNextGenBuildCacheAccess implements NextGenBuildCacheAccess {
    private final NextGenBuildCacheAccess delegate;
    private final BufferProvider bufferProvider;

    public GZipNextGenBuildCacheAccess(NextGenBuildCacheAccess delegate, BufferProvider bufferProvider) {
        this.delegate = delegate;
        this.bufferProvider = bufferProvider;
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
            NextGenBuildCacheService.NextGenWriter delegateWriter = handler.handle(payload);
            // TODO Make this more performant for large files
            UnsynchronizedByteArrayOutputStream compressed = new UnsynchronizedByteArrayOutputStream((int) (delegateWriter.getSize() * 1.2));
            try (GZIPOutputStream zipOutput = new GZIPOutputStream(compressed)) {
                try (InputStream delegateInput = delegateWriter.openStream()) {
                    IOUtils.copyLarge(delegateInput, zipOutput, bufferProvider.getBuffer());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            return new NextGenBuildCacheService.NextGenWriter() {
                @Override
                public InputStream openStream() {
                    return compressed.toInputStream();
                }

                @Override
                public void writeTo(OutputStream output) throws IOException {
                    compressed.writeTo(output);
                }

                @Override
                public long getSize() {
                    return compressed.size();
                }
            };
        });
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
