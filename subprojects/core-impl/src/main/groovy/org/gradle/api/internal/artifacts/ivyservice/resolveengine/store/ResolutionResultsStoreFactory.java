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

import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.CompositeStoppable;
import org.gradle.util.Clock;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ResolutionResultsStoreFactory implements Closeable {
    private final static Logger LOG = Logging.getLogger(ResolutionResultsStoreFactory.class);

    private final TemporaryFileProvider temp;

    private CachedStoreFactory oldModelCache;
    private CachedStoreFactory newModelCache;

    public ResolutionResultsStoreFactory(TemporaryFileProvider temp) {
        this.temp = temp;
    }

    private final Map<String, DefaultBinaryStore> stores = new HashMap<String, DefaultBinaryStore>();

    public BinaryStore createBinaryStore(String id) {
        String storeKey = Thread.currentThread().getId() + id; //one store per thread
        DefaultBinaryStore store = stores.get(storeKey);
        if (store == null) {
            File storeFile = temp.createTemporaryFile("gradle", ".bin");
            storeFile.deleteOnExit();
            store = new DefaultBinaryStore(storeFile);
            stores.put(storeKey, store);
        }
        return store;
    }

    public void close() throws IOException {
        Clock clock = new Clock();
        new CompositeStoppable()
                .add(stores.values())
                .add(oldModelCache)
                .add(newModelCache)
                .stop();
        LOG.debug("Deleted {} resolution results binary files in {}", stores.size(), clock.getTime());
        oldModelCache = null;
        newModelCache = null;
    }

    public <T> Store<T> createOldModelCache(String id) {
        if (oldModelCache == null) {
            oldModelCache = new CachedStoreFactory("Resolution result");
        }
        return oldModelCache.createCachedStore(id);
    }

    public <T> Store<T> createNewModelCache(String id) {
        if (newModelCache == null) {
            newModelCache = new CachedStoreFactory("Resolved configuration");
        }
        return newModelCache.createCachedStore(id);
    }

}