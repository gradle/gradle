/*
 * Copyright 2013 the original author or authors.
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

import java.io.File;
import java.util.Map;

/**
 * The layout strategy for a Cache. This defines a rule to determine the cache directory within a context, as well as any layout-specific cache parameters.
 * The cache is scoped for the given target object. The default is to use a globally-scoped cache.
 */
public interface CacheLayout {
    /**
     * Determine the root directory for a cache with the given key.
     *
     * @param globalCacheDir The global cache directory, which can be used.
     * @param projectCacheDir The project cache directory, if it has been specified on the command line. May be null.
     * @param cacheKey The cache key
     */
    File getCacheDir(File globalCacheDir, File projectCacheDir, String cacheKey);

    /**
     * Adds layout-specific properties to the supplied map.
     */
    Map<String, ?> applyLayoutProperties(Map<String, ?> properties);
}
