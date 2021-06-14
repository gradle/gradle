/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.hash;

import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.CacheVersionMapping;
import org.gradle.cache.internal.VersionStrategy;

import java.io.File;

public class ChecksumCacheLayout {
    private static final CacheVersionMapping CHECKSUMS = CacheVersionMapping.introducedIn("7.2-rc-1")
        .build();

    private final CacheScopeMapping cacheScopeMapping;

    public ChecksumCacheLayout(CacheScopeMapping cacheScopeMapping) {
        this.cacheScopeMapping = cacheScopeMapping;
    }

    public File getCacheDir(File baseDir) {
        return cacheScopeMapping.getBaseDirectory(baseDir, "checksums-" + CHECKSUMS.getLatestVersion(), VersionStrategy.SharedCache);
    }
}
