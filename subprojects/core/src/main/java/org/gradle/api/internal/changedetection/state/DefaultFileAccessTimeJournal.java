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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.Transformer;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.resource.local.FileAccessTimeJournal;

import java.io.File;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;
import static org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER;
import static org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER;

public class DefaultFileAccessTimeJournal implements FileAccessTimeJournal, Stoppable {

    public static final String CACHE_KEY = "journal-1";
    public static final String FILE_ACCESS_CACHE_NAME = "file-access";

    private final PersistentCache cache;
    private final PersistentIndexedCache<File, Long> store;

    public DefaultFileAccessTimeJournal(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory) {
        cache = cacheRepository
            .cache(CACHE_KEY)
            .withCrossVersionCache(CacheBuilder.LockTarget.CacheDirectory)
            .withDisplayName("journal cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // lock on demand
            .open();
        store = cache.createCache(PersistentIndexedCacheParameters.of(FILE_ACCESS_CACHE_NAME, FILE_SERIALIZER, LONG_SERIALIZER)
            .cacheDecorator(cacheDecoratorFactory.decorator(1000, true)));
    }

    @Override
    public void stop() {
        cache.close();
    }

    @Override
    public void setLastAccessTime(File file, long millis) {
        store.put(file, millis);
    }

    @Override
    public long getLastAccessTime(File file) {
        return store.get(file, new Transformer<Long, File>() {
            @Override
            public Long transform(File file) {
                return file.lastModified();
            }
        });
    }
}
