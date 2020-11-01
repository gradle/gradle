/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.initialization.SessionLifecycleListener;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class ConcurrentMapBasedCrossBuildInMemoryCache<K, V> implements CrossBuildInMemoryCache<K, V>, SessionLifecycleListener {
    private final ConcurrentMap<K, V> map = new ConcurrentHashMap<>();
    private final ConcurrentMap<K, Boolean> keysFromPreviousBuild = new ConcurrentHashMap<>();
    private final ConcurrentMap<K, Boolean> keysFromCurrentBuild = new ConcurrentHashMap<>();

    @Override
    public V get(K key, Function<? super K, ? extends V> factory) {
        keysFromCurrentBuild.put(key, Boolean.TRUE);
        return map.computeIfAbsent(key, factory);
    }

    @Override
    public V get(K key) {
        keysFromCurrentBuild.put(key, Boolean.TRUE);
        return map.get(key);
    }

    @Override
    public void put(K key, V value) {
        keysFromCurrentBuild.put(key, Boolean.TRUE);
        map.put(key, value);
    }

    @Override
    public void clear() {
        map.clear();
        keysFromCurrentBuild.clear();
        keysFromPreviousBuild.clear();
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        final Set<K> keysToRetain = new HashSet<>();
        keysToRetain.addAll(keysFromPreviousBuild.keySet());
        keysToRetain.addAll(keysFromCurrentBuild.keySet());

        map.keySet().retainAll(keysToRetain);

        keysFromPreviousBuild.clear();
        keysFromPreviousBuild.putAll(keysFromCurrentBuild);
        keysFromCurrentBuild.clear();
    }
}
