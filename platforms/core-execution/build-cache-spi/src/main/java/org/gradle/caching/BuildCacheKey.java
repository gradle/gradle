/*
 * Copyright 2016 the original author or authors.
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

/**
 * Cache key identifying an entry in the build cache.
 *
 * @since 3.3
 */
public interface BuildCacheKey {
    /**
     * Returns the string representation of the cache key.
     */
    String getHashCode();

    /**
     * Returns the byte array representation of the cache key.
     *
     * @since 5.4
     */
    byte[] toByteArray();

    /**
     * Returns the same as {@link #getHashCode()}.
     *
     * @deprecated Use {@link #getHashCode()} instead.
     *
     * @since 4.2
     */
    @Deprecated
    String getDisplayName();
}
