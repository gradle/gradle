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

import org.gradle.internal.file.FileType;
import org.gradle.internal.serialize.Serializer;

import java.io.File;

/**
 * A factory for caches that contain some calculated value for a particular file. Maintains a cross-build in-memory and persistent cache of computed values. The value for a given file is updated when the content of the file changes.
 */
public interface FileContentCacheFactory {
    /**
     * Creates or locates a cache. The contents of the cache are reused across builds, where possible.
     *
     * @param name An identifier for the cache, used to identify the cache across builds. All instances created using the same identifier will share the same backing store.
     * @param normalizedCacheSize The maximum number of entries to cache in-heap, given a 'typical' heap size. The actual size may vary based on the actual heap available.
     * @param calculator The calculator to use to compute the value for a given file.
     * @param serializer The serializer to use to write values to persistent cache.
     */
    <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, Calculator<? extends V> calculator, Serializer<V> serializer);

    interface Calculator<V> {
        V calculate(File file, FileType fileType);
    }
}
