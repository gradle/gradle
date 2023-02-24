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

package org.gradle.caching.http.internal;

import org.gradle.caching.AsyncBuildCacheService;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class AsyncHttpBuildCacheService implements AsyncBuildCacheService {

    @Override
    public CompletableFuture<Boolean> contains(BuildCacheKey key) {
        return AsyncBuildCacheService.super.contains(key);
    }

    @Override
    public CompletableFuture<Boolean> loadAsync(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        return null;
    }

    @Override
    public CompletableFuture<Void> store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
}
