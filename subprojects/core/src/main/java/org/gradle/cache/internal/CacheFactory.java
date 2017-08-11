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
package org.gradle.cache.internal;

import org.gradle.api.Action;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

public interface CacheFactory {
    /**
     * Opens a cache with the given options. The caller must close the cache when finished with it.
     */
    PersistentCache open(File cacheDir, String displayName, @Nullable CacheValidator cacheValidator, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, @Nullable Action<? super PersistentCache> initializer, @Nullable Action<? super PersistentCache> cleanup) throws CacheOpenException;
}
