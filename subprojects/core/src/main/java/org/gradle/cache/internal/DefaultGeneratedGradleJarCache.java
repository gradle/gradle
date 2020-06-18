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

import org.gradle.api.Action;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.GlobalCache;
import org.gradle.cache.PersistentCache;

import java.io.Closeable;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultGeneratedGradleJarCache implements GeneratedGradleJarCache, Closeable, GlobalCache {

    public static final String BASE_DIR_OVERRIDE_PROPERTY = "org.gradle.cache.internal.generatedGradleJarCacheBaseDir";

    private final PersistentCache cache;
    private final String gradleVersion;

    public DefaultGeneratedGradleJarCache(CacheRepository cacheRepository, String gradleVersion) {
        this.cache = cacheBuilderFor(cacheRepository)
            .withDisplayName(CACHE_DISPLAY_NAME)
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand))
            .open();
        this.gradleVersion = gradleVersion;
    }

    private CacheBuilder cacheBuilderFor(CacheRepository cacheRepository) {
        String baseDirOverride = System.getProperty(BASE_DIR_OVERRIDE_PROPERTY);
        return baseDirOverride != null
            ? cacheRepository.cache(new File(baseDirOverride))
            : cacheRepository.cache(CACHE_KEY);
    }

    @Override
    public File get(final String identifier, final Action<File> creator) {
        final File jarFile = jarFile(identifier);
        cache.useCache(new Runnable() {
            @Override
            public void run() {
                if (!jarFile.exists()) {
                    creator.execute(jarFile);
                }
            }
        });
        return jarFile;
    }

    @Override
    public void close() {
        cache.close();
    }

    private File jarFile(String identifier) {
        return new File(cache.getBaseDir(), "gradle-" + identifier + "-" + gradleVersion + ".jar");
    }

    @Override
    public List<File> getGlobalCacheRoots() {
        return Collections.singletonList(cache.getBaseDir());
    }
}
