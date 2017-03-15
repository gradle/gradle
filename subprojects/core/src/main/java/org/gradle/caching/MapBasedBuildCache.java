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

package org.gradle.caching;

import java.util.concurrent.ConcurrentMap;

/**
 * Simple build cache implementation that delegates to a {@link ConcurrentMap}.
 *
 * @since 3.3
 *
 * @deprecated Use {@link MapBasedBuildCacheService} instead.
 */
@Deprecated
public class MapBasedBuildCache extends MapBasedBuildCacheService implements BuildCache {
    public MapBasedBuildCache(String description, ConcurrentMap<String, byte[]> delegate) {
        super(description, delegate);
    }
}
