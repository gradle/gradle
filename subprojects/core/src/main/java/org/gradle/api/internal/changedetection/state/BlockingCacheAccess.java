/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.CacheAccess;
import org.gradle.cache.internal.AsyncCacheAccess;
import org.gradle.internal.Factory;

public class BlockingCacheAccess implements AsyncCacheAccess {
    private final CacheAccess cacheAccess;

    public BlockingCacheAccess(CacheAccess cacheAccess) {
        this.cacheAccess = cacheAccess;
    }

    @Override
    public <T> T read(Factory<T> task) {
        return cacheAccess.useCache("read from cache", task);
    }

    @Override
    public void enqueue(Runnable task) {
        cacheAccess.useCache("update cache", task);
    }

    @Override
    public void flush() {
    }
}
