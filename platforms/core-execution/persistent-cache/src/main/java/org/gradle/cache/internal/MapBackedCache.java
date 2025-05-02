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
package org.gradle.cache.internal;

import java.util.Map;

public class MapBackedCache<K, V> extends CacheSupport<K, V> {

    private final Map<K, V> map;

    public MapBackedCache(Map<K, V> map) {
        this.map = map;
    }

    @Override
    protected <T extends K> V doGet(T key) {
        return map.get(key);
    }

    @Override
    protected <T extends K, N extends V> void doCache(T key, N value) {
        map.put(key, value);
    }

}
