/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;
import java.util.function.Predicate;

/**
 * A factory for {@link CrossBuildInMemoryCache} instances.
 *
 * Note that this implementation should only be used to create global scoped services.
 * Note that this implementation currently retains strong references to keys and values during the whole lifetime of a build session.
 *
 * Uses a simple algorithm to collect unused values, by retaining strong references to all keys and values used during the current build session, and the previous build session.
 * All other values are referenced only by weak or soft references, allowing them to be collected.
 */
@ThreadSafe
@ServiceScope(Global.class)
public interface CrossBuildInMemoryCacheFactory {
    /**
     * Creates a new cache instance. Keys are always referenced using strong references, values by strong or soft references depending on their usage.
     *
     * <p>Clients should assume that entries may be removed at any time, based on current memory pressure and the likelihood that the entry will be required again soon.
     * The current implementation does not remove an entry during a build session that the entry has been used in, but this is not part of the contract.
     *
     * <p>Note: this should be used to create _only_ global scoped instances.
     */
    <K, V> CrossBuildInMemoryCache<K, V> newCache();

    /**
     * Creates a new cache instance. Keys and values are always referenced using strong references.
     *
     * <p>Entries are only removed after each build session if they have not been used in this or the previous build.
     *
     * <p>Note: this should be used to create _only_ global/Gradle user home scoped instances.
     *
     * @param retentionFilter Determines which values should be retained till the next build.
     */
    <K, V> CrossBuildInMemoryCache<K, V> newCacheRetainingDataFromPreviousBuild(Predicate<V> retentionFilter);

    /**
     * Creates a new cache instance whose keys are Class instances. Keys are referenced using strong or weak references, values by strong or soft references depending on their usage.
     * This allows the classes to be collected.
     *
     * <p>Clients should assume that entries may be removed at any time, based on current memory pressure and the likelihood that the entry will be required again soon.
     * The current implementation does not remove an entry during a build session that the entry has been used in, but this is not part of the contract.
     *
     * <p>Note: this should be used to create _only_ global scoped instances.
     */
    <V> CrossBuildInMemoryCache<Class<?>, V> newClassCache();

    /**
     * Creates a new map instance whose keys are Class instances. Keys are referenced using strong or weak references, values by strong or other references depending on their usage.
     * This allows the classes to be collected.
     *
     * <p>A map differs from a cache in that entries are not discarded based on memory pressure, but are discarded only when the key is collected.
     * You should prefer using a cache instead of a map where possible, and use a map only when generating other classes based on the key.
     *
     * <p>Note: this should be used to create _only_ global scoped instances.
     */
    <V> CrossBuildInMemoryCache<Class<?>, V> newClassMap();
}
