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

package org.gradle.caching.internal;

import org.gradle.internal.hash.HashCode;

import java.util.Collection;
import java.util.Map;

public final class BuildCacheHasherHelper {

    public static void hash(Object value, DefaultBuildCacheHasher cacheHasher) {
        if (value == null) {
            cacheHasher.putNull();
        } else if (value instanceof Byte) {
            cacheHasher.putByte((Byte) value);
        } else if (value instanceof byte[]) {
            cacheHasher.putBytes((byte[]) value);
        } else if (value instanceof HashCode) {
            cacheHasher.putHash((HashCode) value);
        } else if (value instanceof Boolean) {
            cacheHasher.putBoolean((Boolean) value);
        } else if (value instanceof String) {
            cacheHasher.putString((String) value);
        } else if (value instanceof Integer) {
            cacheHasher.putInt((Integer) value);
        } else if (value instanceof Long) {
            cacheHasher.putLong((Long) value);
        } else if (value instanceof Double) {
            cacheHasher.putDouble((Double) value);
        } else if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            cacheHasher.putInt(coll.size());
            for (Object val : coll) {
                hash(val, cacheHasher);
            }
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            cacheHasher.putInt(map.size());
            for (Object val : map.values()) {
                hash(val, cacheHasher);
            }
        } else {
            throw new IllegalStateException("cannot hash " + value.getClass());
        }
    }
}
