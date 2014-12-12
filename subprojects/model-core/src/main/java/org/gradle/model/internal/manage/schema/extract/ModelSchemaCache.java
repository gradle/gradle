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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Type;

@ThreadSafe
public class ModelSchemaCache {

    // Note: this cache is not 100% effective, in that it may contain logically duplicate entries
    //       using weakKeys() forces keys to be compared based on identity, and there may be different
    //       instances for the same logical type floating around
    private final Cache<Type, ModelSchema<?>> cache = CacheBuilder.newBuilder().weakKeys().build();

    @Nullable
    public <T> ModelSchema<T> get(ModelType<T> type) {
        return Cast.uncheckedCast(cache.getIfPresent(type.getRuntimeType()));
    }

    public <T> void set(ModelType<T> type, ModelSchema<T> schema) {
        cache.put(type.getRuntimeType(), schema);
    }

    public long size() {
        return cache.size();
    }

    public void cleanUp() {
        cache.cleanUp();
    }

}
