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
import org.h2.mvstore.MVStore;
import org.h2.mvstore.StreamStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

public class MVStoreBuildCacheService implements StatefulNextGenBuildCacheService {

    private MVStore mvStore;
    private Map<String, byte[]> keys;
    private StreamStore data;
    private final Path dbPath;

    public MVStoreBuildCacheService(Path dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void open() {
        mvStore = new MVStore.Builder()
            .fileName(dbPath.resolve("filestore").toString())
            .autoCompactFillRate(0)
            .open();
        keys = mvStore.openMap("keys");
        Map<Long, byte[]> dataMap = mvStore.openMap("data");
        data = new StreamStore(dataMap);
    }

    @Override
    public boolean contains(BuildCacheKey key) {
        return keys.containsKey(key.getHashCode());
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        byte[] id = keys.get(key.getHashCode());
        if (id == null) {
            return false;
        }
        try (InputStream inputStream = data.get(id)) {
            reader.readFrom(inputStream);
            return true;
        } catch (IOException e) {
            throw new BuildCacheException("loading " + key, e);
        }
    }

    @Override
    public void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException {
        keys.computeIfAbsent(key.getHashCode(), k -> {
            try (InputStream input = writer.openStream()) {
                byte[] id = data.put(input);
                keys.put(key.getHashCode(), id);
                return id;
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
