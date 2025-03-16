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
package org.gradle.cache;

import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * A repository of persistent caches. A cache is a store of persistent data backed by a directory.
 *
 * This is migrating to become an internal type for the caching infrastructure. Please use a subtype of {@link ScopedCacheBuilderFactory}
 * such as {@link GlobalScopedCacheBuilderFactory}
 * or {@link BuildTreeScopedCacheBuilderFactory}
 * or {@link BuildScopedCacheBuilderFactory} instead.
 */
@ServiceScope(Scope.UserHome.class)
public interface UnscopedCacheBuilderFactory {
    /**
     * Returns a builder for the cache with the given base directory. You should prefer one of the other ways of creating a cache with a scoped builder.
     *
     * <p>By default a cache is opened with a shared lock, so that it can be accessed by multiple processes. It is the caller's responsibility
     * to coordinate access to the cache. The initial lock level can be changed using the provided builder </p>
     *
     * <p>Caches created with this method can be inherently cross-version if the path is used by multiple versions of Gradle.</p>
     */
    CacheBuilder cache(File baseDir);
}
