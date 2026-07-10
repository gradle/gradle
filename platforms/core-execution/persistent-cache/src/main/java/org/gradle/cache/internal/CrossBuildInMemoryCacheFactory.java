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
import java.util.function.Consumer;
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
public interface CrossBuildInMemoryCacheFactory extends ClassCacheFactory {
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
     * See {@link #newCache()}.
     *
     * @param onReuse callback triggered when a cached value is reused in a new session after being retained. The callback will be invoked under the cache lock so make it swift.
     */
    <K, V> CrossBuildInMemoryCache<K, V> newCache(Consumer<V> onReuse);

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
     * {@inheritDoc}
     * <p>
     * The current implementation does not remove an entry during a build session that the entry has been used in, but this is not part of the contract.
     * <p>
     * Note: this should be used to create _only_ global scoped instances.
     */
    @Override
    <V> CrossBuildInMemoryCache<Class<?>, V> newClassCache();

    /**
     * {@inheritDoc}
     * <p>
     * Note: this should be used to create _only_ global scoped instances.
     */
    @Override
    <V> CrossBuildInMemoryCache<Class<?>, V> newClassMap();
}
