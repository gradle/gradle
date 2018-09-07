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

import net.jcip.annotations.ThreadSafe;

/**
 * A factory for {@link CrossBuildInMemoryCache} instances.
 *
 * Note that this implementation should only be used to create global scoped services.
 * Note that this implementation currently retains strong references to keys and values during the whole lifetime of a build session.
 *
 * Uses a simple algorithm to collect unused values, by retaining strong references to all keys and values used during the current build session, and the previous build session. All other values are referenced only by soft references.
 *
 * NOTE: this is an abstract class rather than an interface because it is consumed by Kotlin DSL as a class.
 */
@ThreadSafe
public abstract class CrossBuildInMemoryCacheFactory {
    /**
     * Creates a new cache instance. Keys are always referenced using strong references, values by strong or soft references depending on their usage.
     *
     * Note: this should be used to create _only_ global scoped instances.
     */
    public abstract <K, V> CrossBuildInMemoryCache<K, V> newCache();

    /**
     * Creates a new cache instance whose keys are Class instances. Keys are referenced using strong or weak references, values by strong or soft references depending on their usage.
     *
     * Note: this should be used to create _only_ global scoped instances.
     */
    public abstract <V> CrossBuildInMemoryCache<Class<?>, V> newClassCache();
}
