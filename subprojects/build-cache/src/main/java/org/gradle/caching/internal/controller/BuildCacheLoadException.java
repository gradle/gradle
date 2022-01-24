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

package org.gradle.caching.internal.controller;

public class BuildCacheLoadException extends RuntimeException {
    /**
     * Will be one of 'local build cache' or 'remote build cache'
     */
    private final String cacheName;

    private BuildCacheLoadException(String message, String cacheName, Throwable cause) {
        super(message, cause);
        this.cacheName = cacheName;
    }

    public String getCacheName() {
        return cacheName;
    }

    public static BuildCacheLoadException forLocalCache(String entryHashCode, Throwable cause) {
        return exceptionOf("local build cache", entryHashCode, cause);
    }

    public static BuildCacheLoadException forRemoteCache(String entryHashCode, Throwable cause) {
        return exceptionOf("remote build cache", entryHashCode, cause);
    }

    private static BuildCacheLoadException exceptionOf(String cacheName, String entryHashCode, Throwable cause) {
        return new BuildCacheLoadException(String.format("Exception occurred while loading cache entry %s from %s", entryHashCode, cacheName), cacheName, cause);
    }
}
