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

package org.gradle.api.internal.cache;

import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.File;

public interface FileContentCacheFactory {
    /**
     * Creates or locates a cache. The contents of the cache are reused across builds, where possible.
     *
     * @param name An identifier for the cache, used to identify the cache across builds.
     * @param normalizedCacheSize The maximum size of the cache, given a 'typical' heap size. The actual size may vary based on the availability of heap.
     * @param calculator The calculator to use to compute the value for a given file.
     */
    <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, Calculator<? extends V> calculator);

    interface Calculator<V> {
        V calculate(File file, FileType fileType);
    }
}
