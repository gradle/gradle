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

package org.gradle.caching.internal;

import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.IOException;
import java.io.OutputStream;

public class DispatchingBuildCacheService implements BuildCacheService {
    private final BuildCacheService local;
    private final boolean pushToLocal;
    private final BuildCacheService remote;
    private final boolean pushToRemote;

    DispatchingBuildCacheService(BuildCacheService local, boolean pushToLocal, BuildCacheService remote, boolean pushToRemote) {
        this.local = local;
        this.pushToLocal = pushToLocal;
        this.remote = remote;
        this.pushToRemote = pushToRemote;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        return local.load(key, reader) || remote.load(key, reader);
    }

    @Override
    public void store(BuildCacheKey key, final BuildCacheEntryWriter writer) throws BuildCacheException {
        if (pushToLocal) {
            local.store(key, new WriteThroughEntryWriter() {
                @Override
                public void store(BuildCacheKey key, BuildCacheEntryWriter buildCacheEntryWriter) {
                    if (pushToRemote) {
                        remote.store(key, buildCacheEntryWriter);
                    }
                }

                @Override
                public void writeTo(OutputStream output) throws IOException {
                    writer.writeTo(output);
                }
            });
        } else if (pushToRemote) {
            remote.store(key, writer);
        }
    }

    @Override
    public String getDescription() {
        return decorateDescription(pushToLocal, local.getDescription()) + " and " + decorateDescription(pushToRemote, remote.getDescription());
    }

    private String decorateDescription(boolean pushTo, String description) {
        if (pushTo) {
            return description + " (pushing enabled)";
        }
        return description;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(local, remote).stop();
    }
}
