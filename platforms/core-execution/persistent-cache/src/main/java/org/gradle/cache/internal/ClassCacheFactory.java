/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.cache.Cache;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Factory for creating caches for Class instances.
 */
@ServiceScope(Scope.Global.class)
public interface ClassCacheFactory {

    /**
     * Creates a new cache instance whose keys are Class instances. Keys are referenced using
     * strong or weak references, values by strong or soft references depending on their usage.
     * This allows the classes to be collected.
     * <p>
     * Clients should assume that entries may be removed at any time, based on current memory
     * pressure and the likelihood that the entry will be required again soon.
     */
    <V> Cache<Class<?>, V> newClassCache();

    /**
     * Creates a new map instance whose keys are Class instances. Keys are referenced using
     * strong or weak references, values by strong or other references depending on their usage.
     * This allows the classes to be collected.
     * <p>
     * A map differs from a cache in that entries are not discarded based on memory pressure,
     * but are discarded only when the key is collected. You should prefer using a cache instead
     * of a map where possible, and use a map only when generating other classes based on the key.
     */
    <V> Cache<Class<?>, V> newClassMap();

}
