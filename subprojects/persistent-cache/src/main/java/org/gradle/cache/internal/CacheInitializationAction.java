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

package org.gradle.cache.internal;

import org.gradle.cache.FileLock;

public interface CacheInitializationAction {
    /**
     * Determines if this action should run. Called when the cache is opened, holding either a shared or exclusive lock. May be called multiple times.
     */
    boolean requiresInitialization(FileLock fileLock);

    /**
     * Executes the action to initialize the cache. Called only if {@link #requiresInitialization(FileLock)} returns true, holding an exclusive lock.
     * The lock is not released between calling {@link #requiresInitialization(FileLock)} and this method.
     */
    void initialize(FileLock fileLock);
}
