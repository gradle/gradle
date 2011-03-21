/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;

public class CacheBackedFileSnapshotRepository implements FileSnapshotRepository {
    private final CacheRepository repository;
    private PersistentIndexedCache<Object, Object> cache;

    public CacheBackedFileSnapshotRepository(CacheRepository repository) {
        this.repository = repository;
    }

    public Long add(FileCollectionSnapshot snapshot) {
        open();
        Long id = (Long) cache.get("nextId");
        if (id == null) {
            id = 1L;
        }
        cache.put("nextId", id + 1);
        cache.put(id, snapshot);
        return id;
    }

    public FileCollectionSnapshot get(Long id) {
        open();
        return (FileCollectionSnapshot) cache.get(id);
    }

    public void remove(Long id) {
        open();
        cache.remove(id);
    }

    private void open() {
        if (cache == null) {
            cache = repository.cache("fileSnapshots").open().openIndexedCache();
        }
    }
}
