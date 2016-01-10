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

package org.gradle.model.internal.manage.schema.cache;

import com.google.common.collect.Maps;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A multi, volatile, classloader safe cache for model schemas.
 * <p>
 * Significant complexity is introduced by facilitating a volatile classloader environment.
 * That is, classes being loaded and unloaded for the life of this cache.
 * This requires ModelSchema objects to not retain strong class references (which they don't due to use of ModelType)
 * and for strong class references not to be held for the cache keys.
 * <p>
 * The use of {@link WeakClassSet} as the cache key, opposed to just {@link Class}, is because a type may be composed of types from different classloaders.
 * An example of this would be something like {@code ModelSet<SomeCustomUserType>}.
 * The class set abstraction effectively creates a key for all classes involved in a type.
 * The {@link WeakClassSet#isCollected()} method returns true when any of the classes involved have been collected.
 * All keys (and associated entries) are removed from the map by the {@link #cleanUp()} method, which should be invoked periodically to trim the cache of no longer needed data.
 */
public class ModelSchemaCache {
    private final HashMap<WeakClassSet, Map<ModelType<?>, ModelSchema<?>>> cache = Maps.newHashMap();

    @Nullable
    public <T> ModelSchema<T> get(ModelType<T> type) {
        Map<ModelType<?>, ModelSchema<?>> typeCache = cache.get(WeakClassSet.of(type));
        if (typeCache == null) {
            return null;
        } else {
            return Cast.uncheckedCast(typeCache.get(type));
        }
    }

    public <T> void set(ModelType<T> type, ModelSchema<T> schema) {
        WeakClassSet cacheKey = WeakClassSet.of(type);
        Map<ModelType<?>, ModelSchema<?>> typeCache = cache.get(cacheKey);
        if (typeCache == null) {
            typeCache = Maps.newHashMap();
            cache.put(cacheKey, typeCache);
        }
        typeCache.put(type, schema);
    }

    public long size() {
        cleanUp();
        long size = 0;
        for (Map<ModelType<?>, ModelSchema<?>> values : cache.values()) {
            size += values.size();
        }
        return size;
    }

    public void cleanUp() {
        Iterator<Map.Entry<WeakClassSet, Map<ModelType<?>, ModelSchema<?>>>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getKey().isCollected()) {
                iterator.remove();
            }
        }
    }
}
