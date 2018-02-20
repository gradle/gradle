/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching.internal.version2;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.io.IoAction;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@NonNullApi
public class DebuggingLocalBuildCacheServiceV2 implements LocalBuildCacheServiceV2 {
    private static final Logger LOGGER = Logging.getLogger(DebuggingLocalBuildCacheServiceV2.class);
    private final LocalBuildCacheServiceV2 delegate;

    public DebuggingLocalBuildCacheServiceV2(LocalBuildCacheServiceV2 delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public Result getResult(HashCode key) {
        Result result = delegate.getResult(key);
        LOGGER.info("Found result entry {}: {}", key, result);
        return result;
    }

    @Override
    public void getContent(final HashCode key, final ContentProcessor contentProcessor) {
        LOGGER.info("Getting content {}", key);
        delegate.getContent(key, new ContentProcessor() {
            @Override
            public void processFile(InputStream inputStream) throws IOException {
                LOGGER.info("Found a file {}", key);
                contentProcessor.processFile(inputStream);
            }

            @Override
            public void processManifest(ImmutableSortedMap<String, HashCode> entries) throws IOException {
                LOGGER.info("Found manifest {}: {}", key, entries);
                contentProcessor.processManifest(entries);
            }
        });
    }

    @Override
    public void putFile(HashCode key, IoAction<OutputStream> writer) {
        LOGGER.info("Putting file {}", key);
        delegate.putFile(key, writer);
    }

    @Override
    public void putManifest(HashCode key, ImmutableSortedMap<String, HashCode> entries) {
        LOGGER.info("Putting manifest {}: {}", key, entries);
        delegate.putManifest(key, entries);
    }

    @Override
    public void putResult(HashCode key, ImmutableSortedMap<String, HashCode> outputs, byte[] originMetadata) {
        LOGGER.info("Putting results {}: {}", key, outputs);
        delegate.putResult(key, outputs, originMetadata);
    }
}
