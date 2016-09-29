/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.cache.internal.btree;

import org.apache.commons.collections.map.LRUMap;
import org.gradle.api.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CachingBlockStore implements BlockStore {
    private final BlockStore store;
    private final Map<BlockPointer, BlockPayload> dirty = new LinkedHashMap<BlockPointer, BlockPayload>();
    private final Map<BlockPointer, BlockPayload> indexBlockCache = new LRUMap(100);
    private final Set<Class<?>> cachableTypes = new HashSet<Class<?>>();

    public CachingBlockStore(BlockStore store, Class<? extends BlockPayload>... cacheableBlockTypes) {
        this.store = store;
        cachableTypes.addAll(Arrays.asList(cacheableBlockTypes));
    }

    public void open(Runnable initAction, Factory factory) {
        store.open(initAction, factory);
    }

    public void close() {
        flush();
        indexBlockCache.clear();
        store.close();
    }

    public void clear() {
        dirty.clear();
        indexBlockCache.clear();
        store.clear();
    }

    public void flush() {
        Iterator<BlockPayload> iterator = dirty.values().iterator();
        while (iterator.hasNext()) {
            BlockPayload block = iterator.next();
            iterator.remove();
            store.write(block);
        }
        store.flush();
    }

    public void attach(BlockPayload block) {
        store.attach(block);
    }

    public void remove(BlockPayload block) {
        dirty.remove(block.getPos());
        if (isCacheable(block)) {
            indexBlockCache.remove(block.getPos());
        }
        store.remove(block);
    }

    public <T extends BlockPayload> T readFirst(Class<T> payloadType) {
        T block = store.readFirst(payloadType);
        maybeCache(block);
        return block;
    }

    public <T extends BlockPayload> T read(BlockPointer pos, Class<T> payloadType) {
        T block = payloadType.cast(dirty.get(pos));
        if (block != null) {
            return block;
        }
        block = maybeGetFromCache(pos, payloadType);
        if (block != null) {
            return block;
        }
        block = store.read(pos, payloadType);
        maybeCache(block);
        return block;
    }

    @Nullable
    private <T extends BlockPayload> T maybeGetFromCache(BlockPointer pos, Class<T> payloadType) {
        if (cachableTypes.contains(payloadType)) {
            return payloadType.cast(indexBlockCache.get(pos));
        }
        return null;
    }

    public void write(BlockPayload block) {
        store.attach(block);
        maybeCache(block);
        dirty.put(block.getPos(), block);
    }

    private <T extends BlockPayload> void maybeCache(T block) {
        if (isCacheable(block)) {
            indexBlockCache.put(block.getPos(), block);
        }
    }

    private <T extends BlockPayload> boolean isCacheable(T block) {
        return cachableTypes.contains(block.getClass());
    }
}
