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

package org.gradle.cache.internal.scopes;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.scopes.VersionStrategy;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.util.regex.Pattern;

public class DefaultCacheScopeMapping implements CacheScopeMapping {
    @VisibleForTesting
    public static final String GLOBAL_CACHE_DIR_NAME = "caches";
    private static final Pattern CACHE_KEY_NAME_PATTERN = Pattern.compile("\\p{Alpha}+[-/.\\w]*");

    private final File globalCacheDir;
    private final GradleVersion version;

    public DefaultCacheScopeMapping(File rootDir, GradleVersion version) {
        this.globalCacheDir = rootDir;
        this.version = version;
    }

    @Override
    public File getBaseDirectory(@Nullable File baseDir, String key, VersionStrategy versionStrategy) {
        if (!CACHE_KEY_NAME_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(String.format("Unsupported cache key '%s'.", key));
        }
        File cacheRootDir = getRootDirectory(baseDir);
        return getCacheDir(cacheRootDir, versionStrategy, key);
    }

    private File getRootDirectory(@Nullable File scope) {
        if (scope == null) {
            return globalCacheDir;
        } else {
            return scope;
        }
    }

    private File getCacheDir(File rootDir, VersionStrategy versionStrategy, String subDir) {
        switch (versionStrategy) {
            case CachePerVersion:
                return new File(rootDir, version.getVersion() + "/" + subDir);
            case SharedCache:
                return new File(rootDir, subDir);
            default:
                throw new IllegalArgumentException();
        }
    }
}
