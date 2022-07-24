/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store;

import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.Store;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import java.io.Closeable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ResolutionResultsStoreFactory implements Closeable {
    private final static Logger LOG = Logging.getLogger(ResolutionResultsStoreFactory.class);
    private static final int DEFAULT_MAX_SIZE = 2000000000; //2 gigs

    private final TemporaryFileProvider temp;
    private final int maxSize;

    private CachedStoreFactory<TransientConfigurationResults> oldModelCache;
    private CachedStoreFactory<ResolvedComponentResult> newModelCache;

    private final AtomicInteger storeSetBaseId = new AtomicInteger(0);

    public ResolutionResultsStoreFactory(TemporaryFileProvider temp) {
        this(temp, DEFAULT_MAX_SIZE);
    }

    /**
     * @param temp - Provider of temporary files.
     * @param maxSize - indicates the approx. maximum size of the binary store that will trigger rolling of the file
     */
    ResolutionResultsStoreFactory(TemporaryFileProvider temp, int maxSize) {
        this.temp = temp;
        this.maxSize = maxSize;
    }

    private final Map<String, DefaultBinaryStore> stores = new HashMap<>();
    private final CompositeStoppable cleanUpLater = new CompositeStoppable();

    private synchronized DefaultBinaryStore createBinaryStore(String storeKey) {
        DefaultBinaryStore store = stores.get(storeKey);
        if (store == null || isFull(store)) {
            File storeFile = temp.createTemporaryFile("gradle", ".bin");
            storeFile.deleteOnExit();
            store = new DefaultBinaryStore(storeFile);
            stores.put(storeKey, store);
            cleanUpLater.add(store);
        }
        return store;
    }

    private synchronized CachedStoreFactory<TransientConfigurationResults> getOldModelCache() {
        if (oldModelCache == null) {
            oldModelCache = new CachedStoreFactory<>("Resolution result");
            cleanUpLater.add(oldModelCache);
        }
        return oldModelCache;
    }

    private synchronized CachedStoreFactory<ResolvedComponentResult> getNewModelCache() {
        if (newModelCache == null) {
            newModelCache = new CachedStoreFactory<>("Resolution result");
            cleanUpLater.add(newModelCache);
        }
        return newModelCache;
    }

    public StoreSet createStoreSet() {
        return new StoreSet() {
            final int storeSetId = storeSetBaseId.getAndIncrement();
            int binaryStoreId;
            @Override
            public DefaultBinaryStore nextBinaryStore() {
                //one binary store per id+threadId
                String storeKey = Thread.currentThread().getId() + "-" + binaryStoreId++;
                return createBinaryStore(storeKey);
            }

            @Override
            public Store<ResolvedComponentResult> newModelCache() {
                return getNewModelCache().createCachedStore(storeSetId);
            }

            @Override
            public Store<TransientConfigurationResults> oldModelCache() {
                return getOldModelCache().createCachedStore(storeSetId);
            }
        };
    }

    //offset based implementation is only safe up to certain figure
    //because of the int max value
    //for large streams/files (huge builds), we need to roll the file
    //otherwise the stream.size() returns max integer and the offset is no longer correct
    private boolean isFull(DefaultBinaryStore store) {
        return store.getSize() > maxSize;
    }

    @Override
    public void close() {
        try {
            Timer clock = Time.startTimer();
            cleanUpLater.stop();
            LOG.debug("Deleted {} resolution results binary files in {}", stores.size(), clock.getElapsed());
        } finally {
            oldModelCache = null;
            newModelCache = null;
            stores.clear();
        }
    }
}
