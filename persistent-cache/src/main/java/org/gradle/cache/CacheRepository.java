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

import org.gradle.cache.scopes.BuildScopedCache;
import org.gradle.cache.scopes.BuildTreeScopedCache;
import org.gradle.cache.scopes.GlobalScopedCache;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * A repository of persistent caches. A cache is a store of persistent data backed by a directory.
 *
 * This is migrating to become an internal type for the caching infrastructure. Please use
 * {@link GlobalScopedCache}
 * or {@link BuildTreeScopedCache}
 * or {@link BuildScopedCache} instead.
 */
@ServiceScope(Scopes.UserHome.class)
public interface CacheRepository {
    /**
     * Returns a builder for the cache with the given key and global scope. Default is a Gradle version-specific cache shared by all builds, though this
     * can be changed using the provided builder.
     *
     * <p>By default a cache is opened with a shared lock, so that it can be accessed by multiple processes. It is the caller's responsibility
     * to coordinate access to the cache. The initial lock level can be changed using the provided builder </p>
     *
     * @param key The cache key. This is a unique identifier within the cache scope.
     * @return The builder.
     */
    CacheBuilder cache(String key);

    /**
     * Returns a builder for the cache with the given base directory. You should prefer one of the other methods over using this method.
     *
     * <p>By default a cache is opened with a shared lock, so that it can be accessed by multiple processes. It is the caller's responsibility
     * to coordinate access to the cache. The initial lock level can be changed using the provided builder </p>
     */
    CacheBuilder cache(File baseDir);
}
