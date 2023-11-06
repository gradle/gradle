/*
 * Copyright 2022 the original author or authors.
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

import java.io.File;

/**
 * A cache that can be used to store decompressed data extracted from archive files like zip and tars.
 */
public interface DecompressionCache {
    String EXPANSION_CACHE_KEY = "expanded";

    /**
     * Returns the root directory used by this cache to store decompressed files.
     *
     * @return the root directory
     */
    File getBaseDir();

    /**
     * Runs the given action while holding the cache lock.
     */
    void useCache(Runnable action);
}
