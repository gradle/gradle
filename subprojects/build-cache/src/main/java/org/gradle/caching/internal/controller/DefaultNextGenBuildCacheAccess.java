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

import com.google.common.io.Closer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheKey;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class DefaultNextGenBuildCacheAccess implements NextGenBuildCacheAccess {

    // TODO Move all thread-local buffers to a shared service
    // TODO Make buffer size configurable
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final ThreadLocal<byte[]> COPY_BUFFERS = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    private final NextGenBuildCacheHandler local;
    private final NextGenBuildCacheHandler remote;

    public DefaultNextGenBuildCacheAccess(NextGenBuildCacheHandler local, NextGenBuildCacheHandler remote) {
        this.local = local;
        this.remote = remote;
    }

    @Override
    public <T> void load(Map<BuildCacheKey, T> entries, LoadHandler<T> handler) {
        // TODO Fan out to multiple threads
        entries.forEach((key, payload) -> {
            boolean foundLocally = local.load(key, input -> handler.handle(input, payload));
            if (foundLocally) {
                return;
            }
            remote.load(key, input -> {
                // TODO Make this work for large pieces of content, too
                UnsynchronizedByteArrayOutputStream data = new UnsynchronizedByteArrayOutputStream();
                byte[] buffer = COPY_BUFFERS.get();
                IOUtils.copyLarge(input, data, buffer);

                // Mirror data in local cache
                local.store(key, new BuildCacheEntryWriter() {
                    @Override
                    public void writeTo(OutputStream output) throws IOException {
                        IOUtils.copyLarge(data.toInputStream(), output, buffer);
                    }

                    @Override
                    public long getSize() {
                        return data.size();
                    }
                });
                handler.handle(data.toInputStream(), payload);
            });
        });
    }

    @Override
    public <T> void store(Map<BuildCacheKey, T> entries, StoreHandler<T> handler) {
        // TODO Fan out to multiple threads
        entries.forEach((key, payload) -> {
            boolean storeLocal = local.shouldStore(key);
            boolean storeRemote = remote.shouldStore(key);
            if (!storeLocal && !storeRemote) {
                return;
            }

            BuildCacheEntryWriter writer = handler.handle(payload);
            if (storeLocal) {
                local.store(key, writer);
            }
            if (storeRemote) {
                remote.store(key, writer);
            }
        });
    }

    @Override
    public void close() throws IOException {
        Closer closer = Closer.create();
        closer.register(local);
        closer.register(remote);
        closer.close();
    }
}
