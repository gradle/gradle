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

package org.gradle.internal.vfs;

/**
 * Identifies if a path is underneath one of Gradle's "additive caches".
 *
 * Over time more files can be added to an additive cache, but existing files can never be modified.
 * Files in additive caches can only be deleted as part of cleanup.
 *
 * This quasi-immutability of additive caches allows for some optimizations by retaining file system state in-memory.
 */
public interface AdditiveCacheLocations {

    /**
     * Checks if a given path is inside one of Gradle's additive caches.
     */
    boolean isInsideAdditiveCache(String path);
}
