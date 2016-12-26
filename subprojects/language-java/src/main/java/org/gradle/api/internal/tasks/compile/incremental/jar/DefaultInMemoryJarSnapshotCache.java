/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashCode;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class DefaultInMemoryJarSnapshotCache implements JarSnapshotCache {
    private final JarSnapshotCache delegate;

    public DefaultInMemoryJarSnapshotCache(JarSnapshotCache delegate) {
        this.delegate = delegate;
    }

    private final Cache<HashCode, JarSnapshot> inMemoryCache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(5L, TimeUnit.MINUTES)
            .build();

    @Override
    public JarSnapshot get(final HashCode key, final Factory<JarSnapshot> factory) {
        try {
            return inMemoryCache.get(key, new Callable<JarSnapshot>() {
                @Override
                public JarSnapshot call() throws Exception {
                    return delegate.get(key, factory);
                }
            });
        } catch (ExecutionException e) {
            return factory.create();
        }
    }

    @Override
    public Map<File, JarSnapshot> getJarSnapshots(Map<File, HashCode> jarHashes) {
        final Map<File, JarSnapshot> out = new HashMap<File, JarSnapshot>(jarHashes.size());
        for (final Map.Entry<File, HashCode> entry : jarHashes.entrySet()) {
            JarSnapshot snapshot = get(entry.getValue(), new Factory<JarSnapshot>() {
                @Override
                public JarSnapshot create() {
                    throw new IllegalStateException("No Jar snapshot data available for " + entry.getKey() + " with hash " + entry.getValue() + ".");
                }
            });
            out.put(entry.getKey(), snapshot);
        }
        return out;
    }

    @Override
    public void stop() {
        delegate.stop();
    }
}
