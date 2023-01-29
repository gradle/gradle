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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
            try (GZIPInputStream zipInput = new GZIPInputStream(inputStream);) {
                processor.accept(buildCacheKey, zipInput);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void store(Iterable<BuildCacheKey> keys, Function<BuildCacheKey, BuildCacheEntryWriter> processor) {
        delegate.store(keys, buildCacheKey -> {
            BuildCacheEntryWriter delegateWriter = processor.apply(buildCacheKey);
            return new BuildCacheEntryWriter() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    try (GZIPOutputStream zipOutput = new GZIPOutputStream(output)) {
                        delegateWriter.writeTo(zipOutput);
                    }
                }

                @Override
                public long getSize() {
                    return delegateWriter.getSize();
                }
            };
        });
    }
}
