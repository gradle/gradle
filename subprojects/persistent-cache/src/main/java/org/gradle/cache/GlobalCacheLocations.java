/*
 * Copyright 2019 the original author or authors.
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

/**
 * Identifies if a path is underneath one of Gradle's global caches.
 *
 * We expect only Gradle itself to change things in the global caches directories.
 *
 * The quasi-immutability of global caches allows for some optimizations by retaining file system state in-memory.
 */
public interface GlobalCacheLocations {

    /**
     * Checks if a given path is inside one of Gradle's global caches.
     */
    boolean isInsideGlobalCache(String path);
}
