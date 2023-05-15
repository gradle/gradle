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

package org.gradle.caching.local.internal;

import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.StatefulNextGenBuildCacheService;
import org.gradle.caching.local.internal.mvstore.MVStoreLruStreamMap;
import org.h2.mvstore.MVStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class MVStoreBuildCacheService implements StatefulNextGenBuildCacheService {

    private final int maxConcurrency;
    private MVStore mvStore;
    private MVStoreLruStreamMap lruStreamMap;
    private final Path dbPath;

    public MVStoreBuildCacheService(Path dbPath, int maxConcurrency) {
        this.dbPath = dbPath;
        this.maxConcurrency = maxConcurrency;
    }

    @Override
    public void open() {
        mvStore = new MVStore.Builder()
            .fileName(dbPath.resolve("filestore.mvstore.db").toString())
            .autoCompactFillRate(0)
            // 16 is default concurrency level
            .cacheConcurrency(Math.max(maxConcurrency, 16))
            .open();
        lruStreamMap = new MVStoreLruStreamMap(mvStore, maxConcurrency);
    }

    @Override
    public boolean contains(BuildCacheKey key) {
        return lruStreamMap.containsKey(key);
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        try (InputStream inputStream = lruStreamMap.get(key)) {
            if (inputStream != null) {
                reader.readFrom(inputStream);
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new BuildCacheException("loading " + key, e);
        }
    }

    @Override
    public void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException {
        lruStreamMap.putIfAbsent(key, () -> {
            try {
                return writer.openStream();
            } catch (IOException e) {
                throw new BuildCacheException("storing " + key, e);
            }
        });
    }

    @Override
    public void close() {
        mvStore.close();
    }
}
