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

package org.gradle.cache.internal;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;

import java.io.File;

import static org.apache.commons.lang.ArrayUtils.EMPTY_OBJECT_ARRAY;
import static org.apache.commons.lang.ArrayUtils.add;

/**
 * Builds keys suitable to be used with {@link org.gradle.cache.CacheRepository#cache(String)}.
 */
public interface CacheKeyBuilder {

    /**
     * Builds a cache key suitable to be used with {@link org.gradle.cache.CacheRepository#cache(String)}.
     *
     * @param spec the cache key specification
     * @return the cache key
     */
    String build(CacheKeySpec spec);

    /**
     * A cache key specification comprised of a string prefix plus an ordered list of components.
     */
    class CacheKeySpec {

        public static CacheKeySpec withPrefix(String prefix) {
            if (prefix == null || prefix.isEmpty()) {
                throw new IllegalArgumentException("Cache key prefix cannot be null or empty.");
            }
            return new CacheKeySpec(prefix, EMPTY_OBJECT_ARRAY);
        }

        private final String prefix;
        private final Object[] components;

        private CacheKeySpec(String prefix, Object[] components) {
            this.prefix = prefix;
            this.components = components;
        }

        public CacheKeySpec plus(String s) {
            return plusComponent(s);
        }

        public CacheKeySpec plus(File f) {
            return plusComponent(f);
        }

        public CacheKeySpec plus(ClassPath cp) {
            return plusComponent(cp);
        }

        public CacheKeySpec plus(ClassLoader cl) {
            return plusComponent(cl);
        }

        public CacheKeySpec plus(HashCode hashCode) {
            return plusComponent(hashCode);
        }


        String getPrefix() {
            return prefix;
        }

        Object[] getComponents() {
            return components;
        }

        private CacheKeyBuilder.CacheKeySpec plusComponent(Object c) {
            if (c == null) {
                throw new IllegalArgumentException("Cache key component cannot be null.");
            }
            return new CacheKeySpec(prefix, add(components, c));
        }
    }
}
