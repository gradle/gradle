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

package org.gradle.cache.internal;

import org.gradle.cache.GlobalCache;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.file.FileHierarchySet;

import java.io.File;
import java.util.List;

public class DefaultGlobalCacheLocations implements GlobalCacheLocations {
    private final FileHierarchySet globalCacheRoots;

    public DefaultGlobalCacheLocations(List<GlobalCache> globalCaches) {
        FileHierarchySet globalCacheRoots = FileHierarchySet.empty();
        for (GlobalCache globalCache : globalCaches) {
            for (File file : globalCache.getGlobalCacheRoots()) {
                globalCacheRoots = globalCacheRoots.plus(file);
            }
        }
        this.globalCacheRoots = globalCacheRoots;
    }

    @Override
    public boolean isInsideGlobalCache(String path) {
        return globalCacheRoots.contains(path);
    }

    @Override
    public String toString() {
        return globalCacheRoots.toString();
    }
}
